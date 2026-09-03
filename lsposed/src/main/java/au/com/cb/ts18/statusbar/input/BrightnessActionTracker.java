package au.com.cb.ts18.statusbar.input;

/** Pure bounded callback-first confirmation policy for one exact Topway brightness action. */
final class BrightnessActionTracker {
    static final long WRITE_CONFIRM_MS = 1500L;
    static final long QUERY_CONFIRM_MS = 1500L;
    static final long RETRY_CONFIRM_MS = 2500L;
    static final int MAX_WRITE_ATTEMPTS = 2;

    enum DeadlineDecision { WAIT, QUERY, RETRY_WRITE, FAIL }

    private BrightnessActionTracker() {}

    static boolean matches(BrightnessPolicy.Action action, BrightnessPolicy.State state) {
        if (action == null || state == null) return false;
        switch (action.type) {
            case SET_DAY_LEVEL:
                return state.levelsKnown && state.dayLevel == action.value;
            case SET_NIGHT_LEVEL:
                return state.levelsKnown && state.nightLevel == action.value;
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
            case SET_DAY_LEVEL:
            case SET_NIGHT_LEVEL: return "NO_516_CALLBACK";
            default: return "WRITE_UNCONFIRMED";
        }
    }
}
