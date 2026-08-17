package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class NavLayoutPolicyTest {
    @Test
    public void placesAllThreeInConfirmedFreeRegion() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                720,
                Arrays.asList(new NavLayoutPolicy.Interval(0, 180),
                        new NavLayoutPolicy.Interval(540, 720)),
                3, 56, 56, 4, 8);

        assertTrue(result.isSafe());
        assertTrue(result.allRequestedFit);
        assertEquals(3, result.slots.size());
        assertNoOverlap(result, 0, 188);
        assertNoOverlap(result, 532, 720);
    }

    @Test
    public void returnsOnlyConfiguredPrefixThatSafelyFits() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                300,
                Arrays.asList(new NavLayoutPolicy.Interval(0, 70),
                        new NavLayoutPolicy.Interval(210, 300)),
                3, 56, 56, 4, 8);

        assertTrue(result.isSafe());
        assertFalse(result.allRequestedFit);
        assertEquals(2, result.slots.size());
    }

    @Test
    public void refusesToShrinkBelowMinimumTouchTarget() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                720, Collections.emptyList(), 3,
                47, 48, 4, 8);

        assertFalse(result.isSafe());
        assertEquals(NavLayoutPolicy.FailureReason.TARGET_TOO_SMALL,
                result.failureReason);
    }

    @Test
    public void failsOpenWhenStockControlsLeaveNoSafeCell() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                200,
                Arrays.asList(new NavLayoutPolicy.Interval(0, 80),
                        new NavLayoutPolicy.Interval(120, 200)),
                1, 56, 56, 4, 8);

        assertFalse(result.isSafe());
        assertEquals(NavLayoutPolicy.FailureReason.NO_SAFE_FREE_SPACE,
                result.failureReason);
    }

    @Test
    public void overlappingStockIntervalsAreMergedBeforePlacement() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                400,
                Arrays.asList(new NavLayoutPolicy.Interval(0, 100),
                        new NavLayoutPolicy.Interval(90, 180)),
                3, 56, 56, 4, 8);

        assertTrue(result.isSafe());
        assertEquals(3, result.slots.size());
        assertTrue(result.slots.get(0).top >= 188);
    }

    @Test
    public void invalidHierarchyIntervalFailsOpen() {
        NavLayoutPolicy.Result result = NavLayoutPolicy.place(
                400,
                Collections.singletonList(new NavLayoutPolicy.Interval(100, 100)),
                1, 56, 56, 4, 8);

        assertFalse(result.isSafe());
        assertEquals(NavLayoutPolicy.FailureReason.AMBIGUOUS_HIERARCHY,
                result.failureReason);
    }

    private static void assertNoOverlap(NavLayoutPolicy.Result result,
                                        int blockedTop, int blockedBottom) {
        for (NavLayoutPolicy.Slot slot : result.slots) {
            assertFalse(slot.top < blockedBottom && slot.bottom > blockedTop);
        }
    }
}
