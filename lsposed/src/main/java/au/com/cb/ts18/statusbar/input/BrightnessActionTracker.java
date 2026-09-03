package au.com.cb.ts18.statusbar.input;

/** Pure bounded confirmation policy for one exact-device brightness action. */
final class BrightnessActionTracker {
    static final long WRITE_CONFIRM_MS = 1500L;
    static final long QUERY_CONFIRM_MS = 1500L;
    static final long RETRY_CONFIRM_MS = 2500L;
    static final int MAX_WRITE_ATTEMPTS = 2;

    enum DeadlineDecision { WAIT, QUERY, RETRY_WRITE, FAIL }

    private BrightnessActionTracker() {}

    static boolean matches(BrightnessPolicy.Action action, BrightnessPolicy.State state,
                           int observedScreenBrightnessRaw) {
        if (action == null || state == null) return false;
        switch (action.type) {
            case SET_PHYSICAL_LEVEL:
                return observedScreenBrightnessRaw >= 0
                        && action.rawBrightness() == observedScreenBrightnessRaw;
            case SET_MODE:
                return state.modeKnown && state.topwayMode == action.value;
            case NONE:
            default:
                return true;
        }
    }

    static DeadlineDecision onDeadline(long nowMs, long deadlineMs,
                                       int writeAttempts, boolean queriedAfterWrite) {
        if (nowMs < deadlineMs) return DeadlineDecision.WAIT;
        if (!queriedAfterWrite) return DeadlineDecision.QUERY;
        if (writeAttempts < MAX_WRITE_ATTEMPTS) return DeadlineDecision.RETRY_WRITE;
        return DeadlineDecision.FAIL;
    }

    static String missingConfirmationReason(BrightnessPolicy.Action action) {
        if (action == null) return "WRITE_UNCONFIRMED";
        switch (action.type) {
            case SET_MODE: return "NO_258_CALLBACK";
            case SET_PHYSICAL_LEVEL: return "SCREEN_BRIGHTNESS_READBACK_MISMATCH";
            default: return "WRITE_UNCONFIRMED";
        }
    }
}
