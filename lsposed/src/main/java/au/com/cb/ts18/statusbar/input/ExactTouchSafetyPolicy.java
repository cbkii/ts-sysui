package au.com.cb.ts18.statusbar.input;

/** Pure special-state gate applied before ordinary collapsed-region geometry. */
final class ExactTouchSafetyPolicy {
    private ExactTouchSafetyPolicy() {}

    static Decision evaluate(boolean exactIdentity,
                             boolean rootAttached,
                             boolean expanded,
                             boolean keyguardLocked,
                             boolean bouncerShowing,
                             boolean headsUpPinned,
                             boolean headsUpGoingAway,
                             boolean bubblesActive,
                             boolean forceCollapsedUntilLayout) {
        if (!exactIdentity) return Decision.keep("identity");
        if (!rootAttached) return Decision.keep("detached");
        if (expanded) return Decision.keep("expanded");
        if (keyguardLocked) return Decision.keep("keyguard");
        if (bouncerShowing) return Decision.keep("bouncer");
        if (headsUpPinned) return Decision.keep("heads-up-pinned");
        if (headsUpGoingAway) return Decision.keep("heads-up-going-away");
        if (bubblesActive) return Decision.keep("bubbles");
        if (forceCollapsedUntilLayout) return Decision.keep("layout-transition");
        return Decision.apply();
    }

    static final class Decision {
        final boolean apply;
        final String reason;

        private Decision(boolean apply, String reason) {
            this.apply = apply;
            this.reason = reason;
        }

        static Decision apply() {
            return new Decision(true, "ordinary-collapsed");
        }

        static Decision keep(String reason) {
            return new Decision(false, reason);
        }
    }
}
