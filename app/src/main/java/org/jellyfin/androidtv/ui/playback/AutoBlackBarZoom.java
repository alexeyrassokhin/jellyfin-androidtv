package org.jellyfin.androidtv.ui.playback;

import android.graphics.Bitmap;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import java.util.function.BooleanSupplier;

import timber.log.Timber;

/**
 * Opt-in black-bar analysis. PixelCopy samples a tiny bitmap from the video buffer;
 * no change to the decoder, audio pipeline, or subtitle surface is required.
 * All state and callbacks are confined to the playback UI handler.
 */
@OptIn(markerClass = UnstableApi.class)
final class AutoBlackBarZoom {
    private static final int SAMPLE_WIDTH = 160;
    private static final int SAMPLE_HEIGHT = 90;
    private static final long SAMPLE_INTERVAL_MS = 2500;
    private static final int REQUIRED_SAMPLES = 3;
    private static final float SCALE_TOLERANCE = 0.035f;

    private final PlayerView playerView;
    private final VideoSurfaceZoom videoSurfaceZoom;
    private final Handler handler;
    private final BooleanSupplier isPlaying;
    private final int[] pixels = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
    private final Runnable tick = this::sample;

    private boolean active;
    private boolean copyPending;
    private boolean copyErrorLogged;
    private int generation;
    private float appliedScale = 1f;
    private float candidateScale = 1f;
    private int candidateCount;

    AutoBlackBarZoom(PlayerView playerView, VideoSurfaceZoom videoSurfaceZoom, Handler handler, BooleanSupplier isPlaying) {
        this.playerView = playerView;
        this.videoSurfaceZoom = videoSurfaceZoom;
        this.handler = handler;
        this.isPlaying = isPlaying;
    }

    void start() {
        stop();
        active = true;
        candidateCount = 0;
        appliedScale = 1f;
        applyScale(1f);
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS);
    }

    void stop() {
        active = false;
        generation++;
        candidateCount = 0;
        handler.removeCallbacks(tick);
        // Any in-flight copy owns its bitmap and will recycle it in its callback.
    }

    private void schedule() {
        if (active) {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, SAMPLE_INTERVAL_MS);
        }
    }

    private void sample() {
        if (!active) return;
        if (copyPending || !isPlaying.getAsBoolean()) {
            schedule();
            return;
        }
        View view = playerView.getVideoSurfaceView();
        if (!(view instanceof SurfaceView)) {
            if (!copyErrorLogged) {
                Timber.w("Black bar zoom: video surface does not support PixelCopy");
                copyErrorLogged = true;
            }
            schedule();
            return;
        }

        SurfaceView surface = (SurfaceView) view;
        if (!surface.getHolder().getSurface().isValid() || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            schedule();
            return;
        }

        final int requestGeneration = generation;
        final Bitmap bitmap = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888);
        copyPending = true;
        try {
            PixelCopy.request(surface, bitmap, result -> {
                copyPending = false;
                try {
                    if (!active || generation != requestGeneration || !isPlaying.getAsBoolean()) return;
                    if (result != PixelCopy.SUCCESS) {
                        candidateCount = 0;
                        if (!copyErrorLogged) {
                            Timber.w("Black bar zoom: PixelCopy failed (%d); retaining current zoom", result);
                            copyErrorLogged = true;
                        }
                        return;
                    }
                    copyErrorLogged = false;
                    bitmap.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT);
                    BlackBarDetector.Bounds bounds = BlackBarDetector.detect(pixels, SAMPLE_WIDTH, SAMPLE_HEIGHT);
                    if (bounds == null) {
                        candidateCount = 0;
                        return;
                    }
                    float scale = BlackBarDetector.fillScale(bounds, SAMPLE_WIDTH, SAMPLE_HEIGHT,
                            playerView.getWidth(), playerView.getHeight(),
                            videoSurfaceZoom.getBaseWidth(), videoSurfaceZoom.getBaseHeight());
                    acceptSample(scale);
                } finally {
                    bitmap.recycle();
                    schedule();
                }
            }, handler);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            copyPending = false;
            candidateCount = 0;
            bitmap.recycle();
            if (!copyErrorLogged) {
                Timber.w(ex, "Black bar zoom: frame unavailable");
                copyErrorLogged = true;
            }
            schedule();
        }
    }

    private void acceptSample(float scale) {
        // Three consistent frames reduce false changes during dark scenes and transitions.
        if (candidateCount == 0 || Math.abs(candidateScale - scale) > SCALE_TOLERANCE) {
            candidateScale = scale;
            candidateCount = 1;
            return;
        }
        candidateCount = Math.min(REQUIRED_SAMPLES, candidateCount + 1);
        if (candidateCount < REQUIRED_SAMPLES || Math.abs(appliedScale - candidateScale) < SCALE_TOLERANCE) return;
        appliedScale = candidateScale;
        applyScale(appliedScale);
        Timber.d("Black bar zoom: applied %.2fx", appliedScale);
    }

    private void applyScale(float scale) {
        videoSurfaceZoom.setScale(scale);
    }
}
