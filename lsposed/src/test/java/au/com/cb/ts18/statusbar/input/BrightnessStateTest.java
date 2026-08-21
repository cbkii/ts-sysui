package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BrightnessStateTest {
    @Test public void decodesModeCallbackFromThirdArgument() {
        BrightnessState state = new BrightnessState();
        assertTrue(state.acceptCallback(BrightnessProtocol.COMMAND_MODE, 99, 2));
        BrightnessPolicy.State snapshot = state.snapshot();
        assertTrue(snapshot.modeKnown);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_NIGHT, snapshot.topwayMode);
    }
    @Test public void decodesPackedDayNightLevelsAndEffectiveNight() {
        BrightnessState state = new BrightnessState();
        int packed = (10 << 8) | 8;
        assertTrue(state.acceptCallback(BrightnessProtocol.COMMAND_BRIGHTNESS, 1, packed));
        BrightnessPolicy.State snapshot = state.snapshot();
        assertTrue(snapshot.levelsKnown);
        assertEquals(8, snapshot.dayLevel);
        assertEquals(10, snapshot.nightLevel);
        assertTrue(snapshot.effectiveNight);
    }
    @Test public void rejectsOutOfRangePackedLevels() {
        BrightnessState state = new BrightnessState();
        assertFalse(state.acceptCallback(BrightnessProtocol.COMMAND_BRIGHTNESS, 0, (11 << 8) | 8));
        assertFalse(state.snapshot().levelsKnown);
    }
    @Test public void rejectsOutOfRangeModeWithoutChangingKnownState() {
        BrightnessState state = new BrightnessState();
        assertFalse(state.acceptCallback(BrightnessProtocol.COMMAND_MODE, 0, 3));
        assertFalse(state.snapshot().modeKnown);
        assertTrue(state.acceptCallback(BrightnessProtocol.COMMAND_MODE, 0, BrightnessPolicy.TOPWAY_MODE_DAY));
        assertFalse(state.acceptCallback(BrightnessProtocol.COMMAND_MODE, 0, -1));
        BrightnessPolicy.State snapshot = state.snapshot();
        assertTrue(snapshot.modeKnown);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY, snapshot.topwayMode);
    }
    @Test public void ignoresUnexpectedCommandsWithoutChangingState() {
        BrightnessState state = new BrightnessState();
        assertFalse(state.acceptCallback(9999, 1, 2));
        BrightnessPolicy.State snapshot = state.snapshot();
        assertFalse(snapshot.modeKnown);
        assertFalse(snapshot.levelsKnown);
    }
    @Test public void clearResetsKnownFlagsAndValues() {
        BrightnessState state = new BrightnessState();
        assertTrue(state.acceptCallback(BrightnessProtocol.COMMAND_MODE, 0, BrightnessPolicy.TOPWAY_MODE_NIGHT));
        assertTrue(state.acceptCallback(BrightnessProtocol.COMMAND_BRIGHTNESS, 1, (9 << 8) | 7));
        state.clear();
        BrightnessPolicy.State snapshot = state.snapshot();
        assertFalse(snapshot.modeKnown);
        assertFalse(snapshot.levelsKnown);
        assertEquals(0, snapshot.topwayMode);
        assertEquals(0, snapshot.dayLevel);
        assertEquals(0, snapshot.nightLevel);
        assertFalse(snapshot.effectiveNight);
    }
    @Test public void exactModeWriteContractIsPinned() {
        assertEquals(258, BrightnessProtocol.COMMAND_MODE);
        assertEquals(1, BrightnessProtocol.MODE_WRITE_SELECTOR);
        assertEquals(516, BrightnessProtocol.COMMAND_BRIGHTNESS);
        assertEquals(255, BrightnessProtocol.QUERY_VALUE);
    }
}
