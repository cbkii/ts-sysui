package au.com.cb.ts18.statusbar.input;

final class TouchStripGeometry {
    static final int MIN_CORNER_GAP_PX = 64;
    static final float MIN_FRACTION = 0.05f;
    static final float MAX_FRACTION = 0.20f;

    private TouchStripGeometry() {}

    static Result compute(int displayWidth, int rightInset, float requestedFraction,
                          int requestedCornerGapPx) {
        int width = Math.max(1, displayWidth);
        int inset = Math.max(0, Math.min(rightInset, width - 1));
        float fraction = clamp(requestedFraction, MIN_FRACTION, MAX_FRACTION);
        int cornerGap = Math.max(MIN_CORNER_GAP_PX, requestedCornerGapPx);

        // The touch strip is measured in the status-bar window coordinate space.
        // On the exact TS18 that window has historically spanned the full 1280px display.
        // Keep every part of the strip at least cornerGap pixels from BOTH top corners.
        int safeLeft = cornerGap;
        int safeRight = Math.min(width - inset, width - cornerGap);
        if (safeRight <= safeLeft) {
            return Result.invalid(width, inset, fraction, cornerGap);
        }

        // Width is capped against the full display/status-bar width, not the usable width.
        // floor() guarantees the result can never round above the requested fraction.
        int requestedWidth = Math.max(1, (int) Math.floor(width * (double) fraction));
        int maximumWidth = Math.max(1, (int) Math.floor(width * (double) MAX_FRACTION));
        int availableWidth = safeRight - safeLeft;
        int stripWidth = Math.min(Math.min(requestedWidth, maximumWidth), availableWidth);
        if (stripWidth <= 0) {
            return Result.invalid(width, inset, fraction, cornerGap);
        }

        int stripRight = safeRight;
        int stripLeft = stripRight - stripWidth;
        if (stripLeft < safeLeft) {
            stripLeft = safeLeft;
        }
        return new Result(width, stripLeft, stripRight, inset, fraction, cornerGap, true);
    }

    static float clamp(float value, float low, float high) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return low;
        return Math.max(low, Math.min(high, value));
    }

    static final class Result {
        final int displayWidth;
        final int stripLeft;
        final int stripRight;
        final int rightInset;
        final float fraction;
        final int cornerGapPx;
        final boolean valid;

        Result(int displayWidth, int stripLeft, int stripRight, int rightInset,
               float fraction, int cornerGapPx, boolean valid) {
            this.displayWidth = displayWidth;
            this.stripLeft = stripLeft;
            this.stripRight = stripRight;
            this.rightInset = rightInset;
            this.fraction = fraction;
            this.cornerGapPx = cornerGapPx;
            this.valid = valid;
        }

        static Result invalid(int displayWidth, int rightInset, float fraction, int cornerGapPx) {
            return new Result(displayWidth, 0, 0, rightInset, fraction, cornerGapPx, false);
        }

        int stripWidth() { return Math.max(0, stripRight - stripLeft); }
        int leftCornerDistance() { return stripLeft; }
        int rightCornerDistance() { return Math.max(0, displayWidth - stripRight); }
    }
}
