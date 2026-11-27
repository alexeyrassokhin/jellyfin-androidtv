package org.jellyfin.androidtv.ui.playback;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import timber.log.Timber;

/**
 * Enlarges only Media3's video surface by changing its layout dimensions.
 * The surrounding aspect-ratio frame stays at the normal Fit size, providing
 * a stable reference for repeated auto detection and leaving subtitles alone.
 *
 * Avoid View scale transforms on SurfaceView: Shield hardware exhibited
 * replicated edge pixels when that transform exceeded the display bounds.
 */
@OptIn(markerClass = UnstableApi.class)
final class VideoSurfaceZoom {
    private final View surface;
    private final FrameLayout contentFrame;
    private final FrameLayout.LayoutParams originalLayout;
    private final boolean originalClipChildren;
    private final boolean originalClipToPadding;

    private float scale = 1f;
    private boolean customLayout;
    private final View.OnLayoutChangeListener onLayoutChanged =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateLayout();

    VideoSurfaceZoom(PlayerView playerView) {
        surface = playerView.getVideoSurfaceView();
        if (surface != null && surface.getParent() instanceof FrameLayout
                && surface.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            contentFrame = (FrameLayout) surface.getParent();
            originalLayout = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) surface.getLayoutParams());
            originalClipChildren = contentFrame.getClipChildren();
            originalClipToPadding = contentFrame.getClipToPadding();
            contentFrame.addOnLayoutChangeListener(onLayoutChanged);
        } else {
            contentFrame = null;
            originalLayout = null;
            originalClipChildren = true;
            originalClipToPadding = true;
        }
    }

    int getBaseWidth() {
        return contentFrame != null ? contentFrame.getWidth() : 0;
    }

    int getBaseHeight() {
        return contentFrame != null ? contentFrame.getHeight() : 0;
    }

    void setScale(float value) {
        scale = Math.max(1f, Math.min(2f, value));
        if (contentFrame == null) {
            if (scale > 1f) Timber.w("Video zoom: unsupported video surface parent");
            return;
        }

        if (scale <= 1f) {
            restoreLayout();
            return;
        }

        updateLayout();
    }

    private void updateLayout() {
        if (contentFrame == null || scale <= 1f || getBaseWidth() <= 0 || getBaseHeight() <= 0) return;

        int width = Math.round(getBaseWidth() * scale);
        int height = Math.round(getBaseHeight() * scale);
        if (!customLayout) {
            customLayout = true;
            // Allow the centered video surface to extend past the Fit rectangle.
            // The PlayerView/window continues to bound the actual display.
            contentFrame.setClipChildren(false);
            contentFrame.setClipToPadding(false);
        }

        FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (current.width == width && current.height == height
                && current.gravity == Gravity.CENTER
                && current.leftMargin == 0 && current.topMargin == 0
                && current.rightMargin == 0 && current.bottomMargin == 0) return;

        surface.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
        Timber.d("Video zoom: layout %dx%d (base %dx%d, %.2fx)",
                width, height, getBaseWidth(), getBaseHeight(), scale);
    }

    private void restoreLayout() {
        if (!customLayout) return;
        customLayout = false;
        surface.setLayoutParams(new FrameLayout.LayoutParams(originalLayout));
        contentFrame.setClipChildren(originalClipChildren);
        contentFrame.setClipToPadding(originalClipToPadding);
    }

    void release() {
        setScale(1f);
        if (contentFrame != null) contentFrame.removeOnLayoutChangeListener(onLayoutChanged);
    }
}
