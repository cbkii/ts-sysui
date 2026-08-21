package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExactTouchSafetyPolicyTest {
    private static ExactTouchSafetyPolicy.Decision evaluate(
            boolean identity, boolean attached, boolean expanded, boolean keyguard,
            boolean bouncer, boolean pinned, boolean goingAway, boolean bubbles,
            boolean forceCollapsed) {
        return ExactTouchSafetyPolicy.evaluate(identity, attached, expanded, keyguard,
                bouncer, pinned, goingAway, bubbles, forceCollapsed);
    }

    @Test public void ordinaryCollapsedExactStateMayProceed() {
        assertTrue(evaluate(true, true, false, false, false,
                false, false, false, false).apply);
    }

    @Test public void everySpecialOrUnknownAuthorityKeepsStock() {
        assertFalse(evaluate(false, true, false, false, false,
                false, false, false, false).apply);
        assertFalse(evaluate(true, false, false, false, false,
                false, false, false, false).apply);
        assertFalse(evaluate(true, true, true, false, false,
                false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, true, false,
                false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, true,
                false, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                true, false, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, true, false, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, false, true, false).apply);
        assertFalse(evaluate(true, true, false, false, false,
                false, false, false, true).apply);
    }
}
