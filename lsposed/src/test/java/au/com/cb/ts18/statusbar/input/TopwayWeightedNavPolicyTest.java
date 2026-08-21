package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class TopwayWeightedNavPolicyTest {
    @Test public void sixStockAndThreeMediaProjectToEqualEightyPixelCells() {
        TopwayWeightedNavPolicy.Result result = TopwayWeightedNavPolicy.evaluate(
                55, 720, 153f / 160f,
                Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56);
        assertTrue(result.safe);
        assertEquals(80, result.projectedCellPx);
        assertEquals(3f, result.mediaGroupWeight, 0f);
        assertTrue(result.horizontalPreferredMet);
    }

    @Test public void visibleAppSlotStillProjectsAboveProductionTarget() {
        TopwayWeightedNavPolicy.Result result = TopwayWeightedNavPolicy.evaluate(
                55, 720, 153f / 160f,
                Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f, 1f), 3, 56);
        assertTrue(result.safe);
        assertEquals(72, result.projectedCellPx);
    }

    @Test public void stockWidthBetween48And56DpIsAllowedButReportedAsBelowPreferred() {
        TopwayWeightedNavPolicy.Result result = TopwayWeightedNavPolicy.evaluate(
                50, 720, 1f,
                Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56);
        assertTrue(result.safe);
        assertFalse(result.horizontalPreferredMet);
        assertEquals(48, result.minimumHorizontalPx);
        assertEquals(56, result.preferredHorizontalPx);
        assertEquals(50, result.hostWidthPx);
    }

    @Test public void fortyEightDpIsAStopFloorNotProductionVerticalFallback() {
        TopwayWeightedNavPolicy.Result result = TopwayWeightedNavPolicy.evaluate(
                55, 720, 153f / 160f,
                Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 48);
        assertFalse(result.safe);
        assertEquals(TopwayWeightedNavPolicy.FailureReason.TARGET_BELOW_PRODUCTION,
                result.failureReason);
    }

    @Test public void mixedOrNonWeightedStockTopologyFailsOpen() {
        assertFalse(TopwayWeightedNavPolicy.evaluate(
                55, 720, 1f, Arrays.asList(1f, 2f), 3, 56).safe);
        assertFalse(TopwayWeightedNavPolicy.evaluate(
                55, 720, 1f, Arrays.asList(0f, 0f), 3, 56).safe);
    }

    @Test public void below48DpWidthOrInsufficientHeightFailsOpen() {
        TopwayWeightedNavPolicy.Result width = TopwayWeightedNavPolicy.evaluate(
                47, 720, 1f, Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56);
        assertFalse(width.safe);
        assertEquals(TopwayWeightedNavPolicy.FailureReason.WIDTH_BELOW_ABSOLUTE_FLOOR,
                width.failureReason);

        assertFalse(TopwayWeightedNavPolicy.evaluate(
                56, 400, 1f, Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56).safe);
    }
}
