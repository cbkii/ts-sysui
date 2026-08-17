package au.com.cb.ts18.statusbar.input;

/** Pure policy for deciding whether stock InternalInsetsInfo is an ordinary collapsed bar. */
final class TouchableStatePolicy {
    private TouchableStatePolicy() {}

    static Decision evaluate(boolean keyguardLocked,
                             boolean stockEmpty,
                             boolean stockRect,
                             int stockLeft,
                             int stockTop,
                             int stockRight,
                             int stockBottom,
                             int windowWidth,
                             int barHeight,
                             int touchableMode,
                             int regionMode,
                             int trackedWindowHeight) {
        if (keyguardLocked) return Decision.keep("keyguard");
        if (windowWidth <= 1 || barHeight <= 0) return Decision.keep("invalid-dimensions");
        if (touchableMode != regionMode) return Decision.keep("stock-not-region-mode");
        if (stockEmpty) return Decision.keep("stock-region-empty");
        if (!stockRect) return Decision.keep("stock-region-non-rectangular");
        if (stockLeft != 0 || stockTop != 0 || stockRight != windowWidth) {
            return Decision.keep("stock-region-not-full-width");
        }
        int tolerance = Math.max(2, barHeight / 8);
        if (Math.abs(stockBottom - barHeight) > tolerance) {
            return Decision.keep("stock-region-height-mismatch");
        }
        if (trackedWindowHeight <= 0 || Math.abs(trackedWindowHeight - barHeight) > tolerance) {
            return Decision.keep("window-height-not-collapsed");
        }
        return Decision.apply();
    }

    static final class Decision {
        final boolean apply;
        final String reason;
        private Decision(boolean apply, String reason) { this.apply = apply; this.reason = reason; }
        static Decision apply() { return new Decision(true, "collapsed-full-width-region"); }
        static Decision keep(String reason) { return new Decision(false, reason); }
    }
}
