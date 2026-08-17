package au.com.cb.ts18.statusbar.input;

public final class GeometryPolicySelfTest {
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
        eq(960, exact.leftCornerDistance(), "TS18 left-corner distance");
        eq(64, exact.rightCornerDistance(), "TS18 right-corner distance");

        TouchStripGeometry.Result noInset = TouchStripGeometry.compute(1280, 0, .20f, 64);
        yes(noInset.valid, "no-inset geometry valid");
        eq(960, noInset.stripLeft, "no-inset stripLeft");
        eq(1216, noInset.stripRight, "no-inset stripRight");
        eq(256, noInset.stripWidth(), "no-inset stripWidth");

        // A caller asking for more than 20% is hard-capped to 20% of full screen width.
        TouchStripGeometry.Result capped = TouchStripGeometry.compute(1280, 55, .50f, 64);
        yes(capped.valid, "capped geometry valid");
        eq(256, capped.stripWidth(), "20-percent hard width cap");

        // A caller asking for less than the mandatory 64px corner exclusion is clamped upward.
        TouchStripGeometry.Result minGap = TouchStripGeometry.compute(1280, 55, .20f, 0);
        yes(minGap.valid, "minimum-gap geometry valid");
        eq(64, minGap.rightCornerDistance(), "64px mandatory top-right exclusion");

        // A larger right system inset remains authoritative when it already exceeds 64px.
        TouchStripGeometry.Result largerInset = TouchStripGeometry.compute(1280, 100, .20f, 64);
        yes(largerInset.valid, "larger-inset geometry valid");
        eq(1180, largerInset.stripRight, "larger-inset stripRight");
        eq(100, largerInset.rightCornerDistance(), "larger-inset corner distance");
        eq(256, largerInset.stripWidth(), "larger-inset width");

        // Never weaken the 64px rule on an impossibly small surface: report invalid/fail-open.
        TouchStripGeometry.Result impossible = TouchStripGeometry.compute(120, 0, .20f, 64);
        yes(!impossible.valid, "small surface must fail safe instead of weakening corner gap");

        // Sweep representative widths/insets/fractions and assert the user's hard invariants.
        int[] widths = {129, 160, 320, 800, 1024, 1225, 1280, 1920};
        int[] insets = {0, 1, 55, 64, 100, 240};
        float[] fractions = {0.01f, 0.05f, 0.10f, 0.20f, 0.25f, 0.99f};
        for (int width : widths) {
            for (int inset : insets) {
                for (float fraction : fractions) {
                    TouchStripGeometry.Result r = TouchStripGeometry.compute(width, inset, fraction, 64);
                    if (!r.valid) continue;
                    yes(r.leftCornerDistance() >= 64, "left corner clearance width=" + width);
                    yes(r.rightCornerDistance() >= 64, "right corner clearance width=" + width);
                    yes(r.stripWidth() <= (int) Math.floor(width * 0.20d),
                            "20-percent width cap width=" + width);
                    yes(r.stripLeft >= 0 && r.stripRight <= width && r.stripLeft < r.stripRight,
                            "strip bounds width=" + width);
                }
            }
        }

        System.out.println("SUCCESS: geometry policy self-test");
    }
}
