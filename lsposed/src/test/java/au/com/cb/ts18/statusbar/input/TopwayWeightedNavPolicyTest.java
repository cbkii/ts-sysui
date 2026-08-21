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
    }

    @Test public void visibleAppSlotStillProjectsAboveProductionTarget() {
        TopwayWeightedNavPolicy.Result result = TopwayWeightedNavPolicy.evaluate(
                55, 720, 153f / 160f,
                Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f, 1f), 3, 56);
        assertTrue(result.safe);
        assertEquals(72, result.projectedCellPx);
    }

    @Test public void fortyEightDpIsAStopFloorNotProductionFallback() {
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

    @Test public void insufficientWidthOrHeightFailsOpen() {
        assertFalse(TopwayWeightedNavPolicy.evaluate(
                40, 720, 1f, Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56).safe);
        assertFalse(TopwayWeightedNavPolicy.evaluate(
                56, 400, 1f, Arrays.asList(1f, 1f, 1f, 1f, 1f, 1f), 3, 56).safe);
    }
}
