package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BrightnessEventAttributionTest {
    @Test public void exactRequestedRawAfterModuleWriteCorrelatesToModule() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                10_500L, 130, 10_000L, 130, 0L, "none", 0L, 0L);
        assertEquals("module-screen-brightness-write", r.classification);
        assertEquals(500L, r.deltaMs);
    }

    @Test public void eventBeforeModuleWriteDoesNotClaimModuleCausality() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                10_000L, 130, 10_500L, 130, 0L, "none", 0L, 0L);
        assertEquals("external-or-unknown-writer", r.classification);
    }

    @Test public void wrongRawDoesNotClaimModuleCausality() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                10_200L, 140, 10_000L, 130, 0L, "none", 0L, 0L);
        assertEquals("external-or-unknown-writer", r.classification);
    }

    @Test public void nearbyStockTopwayWriteIsOnlyDescribedAsCorrelation() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                20_300L, 180, 0L, -1, 20_000L, "516:1:2570", 0L, 0L);
        assertEquals("external-screen-change-near-stock-topway-write:516:1:2570",
                r.classification);
    }

    @Test public void nearbyStockWriteWinsOverAmbiguousMatchingModuleWrite() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                20_500L, 180, 20_100L, 180, 20_200L, "258:1:2", 0L, 0L);
        assertEquals("external-screen-change-near-stock-topway-write:258:1:2",
                r.classification);
        assertEquals(300L, r.deltaMs);
    }

    @Test public void stockWriteAfterEventDoesNotCorrelateAsWriter() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                20_000L, 180, 0L, -1, 20_500L, "516:1:2570", 0L, 0L);
        assertEquals("external-or-unknown-writer", r.classification);
    }

    @Test public void expiredWindowDoesNotAttribute() {
        BrightnessEventAttribution.Result r = BrightnessEventAttribution.classify(
                30_000L, 180, 20_000L, 180, 20_100L, "516:0:2570", 20_200L, 20_300L);
        assertEquals("external-or-unknown-writer", r.classification);
    }
}
