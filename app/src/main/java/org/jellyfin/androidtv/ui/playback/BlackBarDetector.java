package org.jellyfin.androidtv.ui.playback;

/**
 * Lightweight, platform-independent detector for symmetric, near-black borders.
 * Only accept a border when both edges and an illuminated interior support it.
 */
public final class BlackBarDetector {
    private static final int BLACK_THRESHOLD = 20;
    private static final int BRIGHT_THRESHOLD = 40;

    private BlackBarDetector() {}

    public static final class Bounds {
        public final int horizontalBar;
        public final int verticalBar;

        Bounds(int horizontalBar, int verticalBar) {
            this.horizontalBar = horizontalBar;
            this.verticalBar = verticalBar;
        }

        public float activeWidth(int width) {
            return (width - 2f * verticalBar) / width;
        }

        public float activeHeight(int height) {
            return (height - 2f * horizontalBar) / height;
        }
    }

    /**
     * @return detected symmetric borders (including zero), or null for unreliable frames.
     */
    public static Bounds detect(int[] pixels, int width, int height) {
        if (width < 32 || height < 32 || pixels.length != width * height) return null;

        // Ignore fades and near-black scenes instead of changing the zoom.
        int bright = 0;
        int total = 0;
        for (int y = height / 4; y < 3 * height / 4; y++) {
            for (int x = width / 4; x < 3 * width / 4; x++) {
                if (intensity(pixels[y * width + x]) > BRIGHT_THRESHOLD) bright++;
                total++;
            }
        }
        if (bright * 100 < total * 12) return null;

        int top = edge(pixels, width, height, true, false);
        int bottom = edge(pixels, width, height, true, true);
        int left = edge(pixels, width, height, false, false);
        int right = edge(pixels, width, height, false, true);

        int rows = symmetric(top, bottom, height);
        int columns = symmetric(left, right, width);
        if (rows < 0 || columns < 0) return null;
        return new Bounds(rows, columns);
    }

    private static int intensity(int color) {
        return Math.max((color >>> 16) & 255, Math.max((color >>> 8) & 255, color & 255));
    }

    private static int edge(int[] pixels, int width, int height, boolean horizontal, boolean reverse) {
        int lines = horizontal ? height : width;
        int span = horizontal ? width : height;
        // Ignore outer corners so perpendicular borders don't hide the image.
        int from = span / 4;
        int to = span - from;
        int limit = lines / 3;
        for (int n = 0; n < limit; n++) {
            int line = reverse ? lines - 1 - n : n;
            int dark = 0;
            for (int k = from; k < to; k++) {
                int index = horizontal ? line * width + k : k * width + line;
                if (intensity(pixels[index]) <= BLACK_THRESHOLD) dark++;
            }
            if (dark * 100 < (to - from) * 95) return n;
        }
        // Extreme borders or very dark edges are ambiguous.
        return limit;
    }

    private static int symmetric(int a, int b, int extent) {
        int min = Math.max(2, Math.round(extent * 0.025f));
        if (a < min && b < min) return 0;
        if (a < min || b < min || a >= extent / 3 || b >= extent / 3) return -1;
        if (Math.abs(a - b) > Math.max(2, Math.round(Math.max(a, b) * 0.12f))) return -1;
        return Math.min(a, b);
    }

    /** Scale relative to the normal aspect-preserving Fit surface. */
    public static float fillScale(Bounds bounds, int sampleWidth, int sampleHeight,
                                  int viewWidth, int viewHeight, int surfaceWidth, int surfaceHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return 1f;
        float x = viewWidth / (surfaceWidth * bounds.activeWidth(sampleWidth));
        float y = viewHeight / (surfaceHeight * bounds.activeHeight(sampleHeight));
        return Math.max(1f, Math.min(2f, Math.max(x, y)));
    }
}
