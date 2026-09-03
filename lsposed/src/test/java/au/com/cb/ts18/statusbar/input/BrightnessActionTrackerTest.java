package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrightnessActionTrackerTest {
    @Test public void matchingConfirmationSeparatesModeFromPhysicalReadback() {
        BrightnessPolicy.State state = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_NIGHT,
                true, 9, 4, true);
        assertTrue(BrightnessActionTracker.matches(
                BrightnessPolicy.Action.mode(BrightnessPolicy.TOPWAY_MODE_NIGHT), state, 105));
        BrightnessPolicy.Action physical = BrightnessPolicy.Action.physicalLevel(
                BrightnessPolicy.SLOT_NIGHT, 4);
        assertTrue(BrightnessActionTracker.matches(physical, state,
                BrightnessLevelMapper.logicalToRaw(4)));
        assertFalse(BrightnessActionTracker.matches(physical, state,
                BrightnessLevelMapper.logicalToRaw(5)));
    }

    @Test public void deadlineQueriesBeforeAnyRetry() {
        assertEquals(BrightnessActionTracker.DeadlineDecision.WAIT,
                BrightnessActionTracker.onDeadline(999, 1000, 1, false));
        assertEquals(BrightnessActionTracker.DeadlineDecision.QUERY,
                BrightnessActionTracker.onDeadline(1000, 1000, 1, false));
        assertEquals(BrightnessActionTracker.DeadlineDecision.RETRY_WRITE,
                BrightnessActionTracker.onDeadline(2500, 2000, 1, true));
        assertEquals(BrightnessActionTracker.DeadlineDecision.FAIL,
                BrightnessActionTracker.onDeadline(5000, 4000,
                        BrightnessActionTracker.MAX_WRITE_ATTEMPTS, true));
    }

    @Test public void missingConfirmationReasonDistinguishesBackends() {
        assertEquals("NO_258_CALLBACK", BrightnessActionTracker.missingConfirmationReason(
                BrightnessPolicy.Action.mode(BrightnessPolicy.TOPWAY_MODE_DAY)));
        assertEquals("SCREEN_BRIGHTNESS_READBACK_MISMATCH",
                BrightnessActionTracker.missingConfirmationReason(
                        BrightnessPolicy.Action.physicalLevel(BrightnessPolicy.SLOT_NIGHT, 4)));
    }
}
