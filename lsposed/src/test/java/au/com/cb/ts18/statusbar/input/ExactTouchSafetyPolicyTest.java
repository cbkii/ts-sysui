package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExactTouchSafetyPolicyTest {
    private static ExactTouchSafetyPolicy.Decision evaluate(
            boolean identity, boolean attached, boolean expanded, boolean keyguard,
            boolean bouncer, boolean pinned, boolean goingAway, boolean bubbles,
            boolean forceCollapsed, boolean stockShouldAdjustInsets) {
        return ExactTouchSafetyPolicy.evaluate(identity, attached, expanded, keyguard,
                bouncer, pinned, goingAway, bubbles, forceCollapsed, stockShouldAdjustInsets);
    }

    @Test public void ordinaryCollapsedExactStateMayProceedFromDefaultInsetsState() {
        assertTrue(evaluate(true, true, false, false, false,
                false, false, false, false, false).apply);
    }

    @Test public void stockShouldAdjustInsetsRetainsCompleteStockOwnership() {
        ExactTouchSafetyPolicy.Decision decision = evaluate(true, true, false, false, false,
                false, false, false, false, true);
        assertFalse(decision.apply);
        assertEquals("stock-adjusting-insets", decision.reason);
    }

    @Test public void everySpecialOrUnknownAuthorityKeepsStock() {
        assertFalse(evaluate(false, true, false, false, false,
                false, false, false, false, false).apply);
        assertFalse(evaluate(true, false, false, false, false,
                false, false, false, false, false).apply);
        assertFalse(evaluate(true, true, true, false, false,
                false, false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, true, false,
                false, false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, true,
                false, false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                true, false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, true, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, false, true, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, false, false, true, false).apply);
    }
}
