package au.com.cb.ts18.statusbar.input;

/** Pure fail-open policy for stock Topway navigation-panel visibility. */
final class NavVisibilityPolicy {
    private NavVisibilityPolicy() {}

    static Decision evaluate(boolean rootAttached,
                             boolean rootVisible,
                             boolean rootWindowVisible,
                             boolean rootShown,
                             boolean hostPresent,
                             boolean hostVisible,
                             boolean hostShown) {
        if (!rootAttached) return Decision.hidden("root-detached");
        if (!rootVisible) return Decision.hidden("root-hidden");
        if (!rootWindowVisible) return Decision.hidden("root-window-hidden");
        if (!rootShown) return Decision.hidden("root-not-shown");
        if (!hostPresent) return Decision.hidden("navbar_left-missing");
        if (!hostVisible) return Decision.hidden("navbar_left-hidden");
        if (!hostShown) return Decision.hidden("navbar_left-not-shown");
        return Decision.visible();
    }

    static final class Decision {
        final boolean visible;
        final String reason;

        private Decision(boolean visible, String reason) {
            this.visible = visible;
            this.reason = reason;
        }

        static Decision visible() {
            return new Decision(true, "visible");
        }

        static Decision hidden(String reason) {
            return new Decision(false, reason);
        }
    }
}
