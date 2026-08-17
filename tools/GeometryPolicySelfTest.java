package au.com.cb.ts18.statusbar.input;

/** Host-only smoke coverage for pure safety policies; Android/JUnit CI adds deeper coverage. */
public final class GeometryPolicySelfTest {
    private GeometryPolicySelfTest() {}

    private static void eq(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void yes(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        TouchStripGeometry.Result exact = TouchStripGeometry.compute(1280, 55, .20f, 64);
        yes(exact.valid, "exact TS18 geometry valid");
        eq(960, exact.stripLeft, "TS18 stripLeft");
        eq(1216, exact.stripRight, "TS18 stripRight");
        eq(256, exact.stripWidth(), "TS18 stripWidth");
        eq(64, exact.rightCornerDistance(), "TS18 right-corner distance");

        TouchStripGeometry.Result onePercent = TouchStripGeometry.compute(1280, 55, .01f, 64);
        yes(onePercent.valid, "1-percent geometry valid");
        eq(12, onePercent.stripWidth(), "1-percent width");

        TouchStripGeometry.Result capped = TouchStripGeometry.compute(1280, 55, .99f, 64);
        eq(256, capped.stripWidth(), "20-percent hard width cap");

        TouchStripGeometry.Result impossible = TouchStripGeometry.compute(120, 0, .20f, 64);
        yes(!impossible.valid, "small surface must fail open");

        int[] widths = {129, 160, 320, 800, 1024, 1225, 1280, 1920};
        int[] insets = {0, 1, 55, 64, 100, 240};
        float[] fractions = {0.01f, 0.02f, 0.05f, 0.10f, 0.20f, 0.25f, 0.99f};
        for (int width : widths) {
            for (int inset : insets) {
                for (float fraction : fractions) {
                    TouchStripGeometry.Result r = TouchStripGeometry.compute(width, inset, fraction, 64);
                    if (!r.valid) continue;
                    yes(r.leftCornerDistance() >= 64, "left corner clearance width=" + width);
                    yes(r.rightCornerDistance() >= 64, "right corner clearance width=" + width);
                    yes(r.stripWidth() <= (int) Math.floor(width * 0.20d),
                            "20-percent width cap width=" + width);
                }
            }
        }

        yes(CoordinateSpacePolicy.evaluate(0, 0, 1280, 1280).valid,
                "full-width physical coordinate mapping");
        yes(!CoordinateSpacePolicy.evaluate(0, 0, 1225, 1280).valid,
                "partial-width coordinate mapping must fail open");

        TouchableStatePolicy.Decision collapsed = TouchableStatePolicy.evaluate(
                false, false, true, 0, 0, 1280, 41, 1280, 41, 3, 3, 41);
        yes(collapsed.apply, "ordinary collapsed region eligible");
        yes(!TouchableStatePolicy.evaluate(
                false, false, true, 900, 0, 1200, 41, 1280, 41, 3, 3, 41).apply,
                "heads-up-like region kept stock");
        yes(!TouchableStatePolicy.evaluate(
                false, false, true, 0, 0, 1280, 41, 1280, 41, 3, 3, 720).apply,
                "expanded window kept stock");

        yes(VisualScalePolicy.decide(true, false, false, true)
                        == VisualScalePolicy.Action.RESTORE_AND_RELEASE,
                "owned leaf leaving bar restores");
        yes(VisualScalePolicy.decide(true, false, true, false)
                        == VisualScalePolicy.Action.RELEASE_CONFLICT,
                "external scale change releases ownership");

        System.out.println("SUCCESS: geometry/runtime policy self-test");
    }
}
