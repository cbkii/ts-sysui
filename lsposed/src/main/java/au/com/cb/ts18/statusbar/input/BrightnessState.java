package au.com.cb.ts18.statusbar.input;

/** Process-local semantic state learned only from Topway callbacks. */
final class BrightnessState {
    private boolean modeKnown;
    private int topwayMode;
    private boolean levelsKnown;
    private int dayLevel;
    private int nightLevel;
    private boolean effectiveNight;

    synchronized boolean acceptCallback(int command, int arg1, int arg2) {
        if (command == BrightnessProtocol.COMMAND_MODE) {
            if (arg2 < BrightnessPolicy.TOPWAY_MODE_AUTO || arg2 > BrightnessPolicy.TOPWAY_MODE_NIGHT) return false;
            boolean enteringAuto = arg2 == BrightnessPolicy.TOPWAY_MODE_AUTO
                    && (!modeKnown || topwayMode != BrightnessPolicy.TOPWAY_MODE_AUTO);
            modeKnown = true;
            topwayMode = arg2;
            if (enteringAuto) invalidate516ObservationLocked();
            return true;
        }
        if (command == BrightnessProtocol.COMMAND_BRIGHTNESS) {
            int day = BrightnessProtocol.dayFromPackedLevels(arg2);
            int night = BrightnessProtocol.nightFromPackedLevels(arg2);
            if (day > BrightnessPolicy.MAX_LEVEL || night > BrightnessPolicy.MAX_LEVEL) return false;
            levelsKnown = true;
            dayLevel = day;
            nightLevel = night;
            effectiveNight = arg1 == 1;
            return true;
        }
        return false;
    }

    synchronized BrightnessPolicy.State snapshot() {
        return new BrightnessPolicy.State(modeKnown, topwayMode, levelsKnown,
                dayLevel, nightLevel, effectiveNight);
    }

    /** Require a new 516 callback before stock-Auto effective Day/Night is trusted again. */
    synchronized void invalidate516Observation() {
        invalidate516ObservationLocked();
    }

    synchronized void clear() {
        modeKnown = false;
        topwayMode = 0;
        invalidate516ObservationLocked();
    }

    private void invalidate516ObservationLocked() {
        levelsKnown = false;
        dayLevel = 0;
        nightLevel = 0;
        effectiveNight = false;
    }
}
