package au.com.cb.ts18.statusbar.input;

/** Pure physical/window coordinate contract used before applying physical corner rules. */
final class CoordinateSpacePolicy {
    private CoordinateSpacePolicy() {}

    static Result evaluate(int rootX, int rootY, int rootWidth, int physicalWidth) {
        if (physicalWidth <= 1 || rootWidth <= 1) return Result.invalid("invalid-width");
        if (rootX != 0 || rootY != 0) return Result.invalid("statusbar-origin-not-physical-top-left");
        if (rootWidth != physicalWidth) return Result.invalid("statusbar-width-not-physical-width");
        return Result.valid();
    }

    static final class Result {
        final boolean valid;
        final String reason;

        private Result(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        static Result valid() { return new Result(true, "one-to-one-physical-top-edge"); }
        static Result invalid(String reason) { return new Result(false, reason); }
    }
}
