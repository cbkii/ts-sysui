package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VehicleStatePolicyTest {
    @Test public void initialUnknownStateDoesNotReplaceStockVisibilityAuthority() {
        VehicleStatePolicy.Decision d = VehicleStatePolicy.evaluate(
                false, -1, false, false, -1, false);
        assertTrue(d.allowNavMedia);
        assertEquals("no-known-vehicle-veto", d.reason);
    }

    @Test public void reverseActiveVetoes() {
        VehicleStatePolicy.Decision d = VehicleStatePolicy.evaluate(
                true, 1, true, true, 0, false);
        assertFalse(d.allowNavMedia);
        assertEquals("reverse-active", d.reason);
    }

    @Test public void sleepActiveVetoes() {
        VehicleStatePolicy.Decision d = VehicleStatePolicy.evaluate(
                true, 0, false, true, 1, true);
        assertFalse(d.allowNavMedia);
        assertEquals("sleep-active", d.reason);
    }

    @Test public void unknownAfterActiveKeepsVetoUntilFreshInactiveState() {
        VehicleStatePolicy.Decision stale = VehicleStatePolicy.evaluate(
                false, -1, true, true, 0, false);
        assertFalse(stale.allowNavMedia);
        assertEquals("reverse-state-stale-after-active", stale.reason);

        VehicleStatePolicy.Decision fresh = VehicleStatePolicy.evaluate(
                true, 0, false, true, 0, false);
        assertTrue(fresh.allowNavMedia);
    }
}
