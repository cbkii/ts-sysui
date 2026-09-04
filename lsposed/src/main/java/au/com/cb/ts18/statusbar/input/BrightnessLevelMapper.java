package au.com.cb.ts18.statusbar.input;

/** Exact CarSetting-backed mapping between managed 1..10 levels and raw Android brightness. */
final class BrightnessLevelMapper {
    static final int RAW_MIN = 30;
    static final int RAW_MAX = 255;
    static final int RAW_STEP = 25;

    private BrightnessLevelMapper() {}

    static int logicalToRaw(int logicalLevel) {
        if (logicalLevel < BrightnessPolicy.MIN_LEVEL
                || logicalLevel > BrightnessPolicy.MAX_LEVEL) {
            throw new IllegalArgumentException("logical brightness must be 1..10");
        }
        return RAW_MIN + (logicalLevel - BrightnessPolicy.MIN_LEVEL) * RAW_STEP;
    }

    static int rawToNearestLogical(int rawBrightness) {
        int clamped = Math.max(RAW_MIN, Math.min(RAW_MAX, rawBrightness));
        int logical = BrightnessPolicy.MIN_LEVEL
                + Math.round((clamped - RAW_MIN) / (float) RAW_STEP);
        return Math.max(BrightnessPolicy.MIN_LEVEL,
                Math.min(BrightnessPolicy.MAX_LEVEL, logical));
    }

    static boolean matchesLogical(int logicalLevel, int rawBrightness) {
        return logicalLevel >= BrightnessPolicy.MIN_LEVEL
                && logicalLevel <= BrightnessPolicy.MAX_LEVEL
                && logicalToRaw(logicalLevel) == rawBrightness;
    }
}
