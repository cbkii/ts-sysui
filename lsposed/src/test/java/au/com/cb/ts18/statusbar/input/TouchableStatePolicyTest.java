package au.com.cb.ts18.statusbar.input;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TouchableStatePolicyTest {
    private static TouchableStatePolicy.Decision decide(boolean keyguard, boolean empty, boolean rect,
                                                        int left, int top, int right, int bottom,
                                                        int mode, int trackedHeight) {
        return TouchableStatePolicy.evaluate(keyguard, empty, rect, left, top, right, bottom,
                1280, 41, mode, 3, trackedHeight);
    }
    @Test public void exactCollapsedFullWidthRegionIsEligible() { assertTrue(decide(false,false,true,0,0,1280,41,3,41).apply); }
    @Test public void keyguardIsAlwaysStock() { assertFalse(decide(true,false,true,0,0,1280,41,3,41).apply); }
    @Test public void emptyRegionIsStock() { assertFalse(decide(false,true,true,0,0,1280,41,3,41).apply); }
    @Test public void nonRectangularRegionIsStock() { assertFalse(decide(false,false,false,0,0,1280,41,3,41).apply); }
    @Test public void headsUpLikeNarrowRegionIsStock() { assertFalse(decide(false,false,true,900,0,1200,41,3,41).apply); }
    @Test public void expandedOrOldHeightRegionIsStock() { assertFalse(decide(false,false,true,0,0,1280,55,3,41).apply); }
    @Test public void nonRegionTouchableModeIsStock() { assertFalse(decide(false,false,true,0,0,1280,41,1,41).apply); }
    @Test public void unknownOrExpandedWindowHeightIsStock() {
        assertFalse(decide(false,false,true,0,0,1280,41,3,-1).apply);
        assertFalse(decide(false,false,true,0,0,1280,41,3,720).apply);
    }
}
