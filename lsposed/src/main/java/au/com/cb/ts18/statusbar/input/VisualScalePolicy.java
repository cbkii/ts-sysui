package au.com.cb.ts18.statusbar.input;

final class VisualScalePolicy {
    enum Action { CAPTURE_AND_APPLY, APPLY_OWNED, RESTORE_AND_RELEASE, RELEASE_CONFLICT, SKIP }
    private VisualScalePolicy() {}
    static Action decide(boolean owned, boolean conflicted, boolean inTarget, boolean currentMatchesApplied) {
        if (conflicted) return Action.SKIP;
        if (!owned) return inTarget ? Action.CAPTURE_AND_APPLY : Action.SKIP;
        if (!currentMatchesApplied) return Action.RELEASE_CONFLICT;
        return inTarget ? Action.APPLY_OWNED : Action.RESTORE_AND_RELEASE;
    }
}
