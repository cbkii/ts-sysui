package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrightnessLevelMapperTest {
    @Test public void exactCarSettingBoundsArePreserved() {
        assertEquals(30, BrightnessLevelMapper.logicalToRaw(1));
        assertEquals(255, BrightnessLevelMapper.logicalToRaw(10));
    }

    @Test public void mappingIsLinearMonotonicAndRoundTripsManagedLevels() {
        int previous = -1;
        for (int level = 1; level <= 10; level++) {
            int raw = BrightnessLevelMapper.logicalToRaw(level);
            assertEquals(30 + (level - 1) * 25, raw);
            assertTrue(raw > previous);
            assertEquals(level, BrightnessLevelMapper.rawToNearestLogical(raw));
            assertTrue(BrightnessLevelMapper.matchesLogical(level, raw));
            previous = raw;
        }
    }

    @Test public void mismatchedReadbackDoesNotConfirm() {
        assertFalse(BrightnessLevelMapper.matchesLogical(4, 130));
        assertTrue(BrightnessLevelMapper.matchesLogical(4, 105));
    }

    @Test(expected = IllegalArgumentException.class)
    public void levelZeroCannotMapToPhysicalBrightness() {
        BrightnessLevelMapper.logicalToRaw(0);
    }
}
