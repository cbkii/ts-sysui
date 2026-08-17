package au.com.cb.ts18.statusbar.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TouchStripGeometryTest {
    @Test public void exactTs18GeometryHonoursCornerAndWidthBounds() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 55, 0.20f, 64);
        assertTrue(r.valid);
        assertEquals(960, r.stripLeft);
        assertEquals(1216, r.stripRight);
        assertEquals(256, r.stripWidth());
        assertEquals(64, r.displayWidth - r.stripRight);
    }

    @Test public void onePercentIsAllowedInsteadOfBeingRaisedToFivePercent() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 55, 0.01f, 64);
        assertTrue(r.valid);
        assertEquals(0.01f, r.fraction, 0.0001f);
        assertEquals(12, r.stripWidth());
        assertEquals(1204, r.stripLeft);
        assertEquals(1216, r.stripRight);
    }

    @Test public void noRightInsetStillLeavesPhysicalCornerGap() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 0, 0.20f, 64);
        assertTrue(r.valid);
        assertEquals(960, r.stripLeft);
        assertEquals(1216, r.stripRight);
        assertEquals(256, r.stripWidth());
    }

    @Test public void fractionCannotExceedTwentyPercent() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 55, 0.99f, 64);
        assertTrue(r.valid);
        assertEquals(0.20f, r.fraction, 0.0001f);
        assertEquals(256, r.stripWidth());
    }

    @Test public void cornerGapCannotBeConfiguredBelowSixtyFourPixels() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 55, 0.20f, 0);
        assertTrue(r.valid);
        assertEquals(64, r.cornerGapPx);
        assertEquals(1216, r.stripRight);
    }

    @Test public void largerRightInsetWinsOverMinimumCornerGap() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(1280, 100, 0.20f, 64);
        assertTrue(r.valid);
        assertEquals(1180, r.stripRight);
        assertEquals(100, r.displayWidth - r.stripRight);
    }

    @Test public void impossibleSurfaceFailsOpenInsteadOfWeakeningSafetyRule() {
        TouchStripGeometry.Result r = TouchStripGeometry.compute(120, 0, 0.20f, 64);
        assertFalse(r.valid);
        assertEquals(0, r.stripWidth());
    }
}
