package au.com.cb.ts18.statusbar.input;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoordinateSpacePolicyTest {
    @Test public void exactPhysicalTopEdgeIsAccepted() { assertTrue(CoordinateSpacePolicy.evaluate(0, 0, 1280, 1280).valid); }
    @Test public void horizontalOffsetFailsOpen() { assertFalse(CoordinateSpacePolicy.evaluate(1, 0, 1279, 1280).valid); }
    @Test public void verticalOffsetFailsOpen() { assertFalse(CoordinateSpacePolicy.evaluate(0, 1, 1280, 1280).valid); }
    @Test public void partialWidthWindowFailsOpen() { assertFalse(CoordinateSpacePolicy.evaluate(0, 0, 1225, 1280).valid); }
}
