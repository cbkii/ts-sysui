package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavVisibilityPolicyTest {
    @Test public void fullyVisibleStockPanelMayRunPreflight() {
        NavVisibilityPolicy.Decision decision = NavVisibilityPolicy.evaluate(
                true, true, true, true, true, true, true);
        assertTrue(decision.visible);
        assertEquals("visible", decision.reason);
    }

    @Test public void everyHiddenOrMissingSurfaceFailsOpen() {
        assertFalse(NavVisibilityPolicy.evaluate(false, true, true, true, true, true, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, false, true, true, true, true, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, true, false, true, true, true, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, true, true, false, true, true, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, true, true, true, false, true, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, true, true, true, true, false, true).visible);
        assertFalse(NavVisibilityPolicy.evaluate(true, true, true, true, true, true, false).visible);
    }

    @Test public void stockVisibilityReturnIsEligibleForFullRepreflight() {
        assertFalse(NavVisibilityPolicy.evaluate(
                true, true, true, true, true, false, false).visible);
        assertTrue(NavVisibilityPolicy.evaluate(
                true, true, true, true, true, true, true).visible);
    }
}
