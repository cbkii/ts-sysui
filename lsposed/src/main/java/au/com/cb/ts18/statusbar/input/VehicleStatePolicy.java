package au.com.cb.ts18.statusbar.input;

/** Pure admission policy for read-only Topway reverse/sleep observations. */
final class VehicleStatePolicy {
    private VehicleStatePolicy() {}

    static Decision evaluate(boolean reverseKnown, int reverseStatus,
                             boolean reverseLastKnownActive,
                             boolean sleepKnown, int sleepStatus,
                             boolean sleepLastKnownActive) {
        if (reverseKnown && reverseStatus == 1) return Decision.veto("reverse-active");
        if (sleepKnown && sleepStatus == 1) return Decision.veto("sleep-active");
        if (!reverseKnown && reverseLastKnownActive) {
            return Decision.veto("reverse-state-stale-after-active");
        }
        if (!sleepKnown && sleepLastKnownActive) {
            return Decision.veto("sleep-state-stale-after-active");
        }
        return Decision.allow("no-known-vehicle-veto");
    }

    static final class Decision {
        final boolean allowNavMedia;
        final String reason;

        private Decision(boolean allowNavMedia, String reason) {
            this.allowNavMedia = allowNavMedia;
            this.reason = reason;
        }

        static Decision allow(String reason) { return new Decision(true, reason); }
        static Decision veto(String reason) { return new Decision(false, reason); }
    }
}
