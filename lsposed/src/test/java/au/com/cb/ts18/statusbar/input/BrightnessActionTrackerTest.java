package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrightnessActionTrackerTest {
    @Test public void matchingConfirmationSeparates258From516() {
        BrightnessPolicy.State state = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_NIGHT,
                true, 9, 4, true);
        assertTrue(BrightnessActionTracker.matches(
                BrightnessPolicy.Action.mode(BrightnessPolicy.TOPWAY_MODE_NIGHT), state));
        assertTrue(BrightnessActionTracker.matches(BrightnessPolicy.Action.dayLevel(9), state));
        assertTrue(BrightnessActionTracker.matches(BrightnessPolicy.Action.nightLevel(4), state));
        assertFalse(BrightnessActionTracker.matches(BrightnessPolicy.Action.dayLevel(8), state));
        assertFalse(BrightnessActionTracker.matches(BrightnessPolicy.Action.nightLevel(5), state));
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

    @Test public void missingConfirmationReasonDistinguishesCallbacks() {
        assertEquals("NO_258_CALLBACK", BrightnessActionTracker.missingConfirmationReason(
                BrightnessPolicy.Action.mode(BrightnessPolicy.TOPWAY_MODE_DAY)));
        assertEquals("NO_516_CALLBACK", BrightnessActionTracker.missingConfirmationReason(
                BrightnessPolicy.Action.dayLevel(8)));
        assertEquals("NO_516_CALLBACK", BrightnessActionTracker.missingConfirmationReason(
                BrightnessPolicy.Action.nightLevel(4)));
    }
}
