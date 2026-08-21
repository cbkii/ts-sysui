package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BrightnessPolicyTest {
    @Test public void scheduledDayNormalWindow() {
        assertFalse(BrightnessPolicy.isScheduledDay(420, 1140, 419));
        assertTrue(BrightnessPolicy.isScheduledDay(420, 1140, 420));
        assertTrue(BrightnessPolicy.isScheduledDay(420, 1140, 1139));
        assertFalse(BrightnessPolicy.isScheduledDay(420, 1140, 1140));
    }
    @Test public void scheduledDaySupportsWindowAcrossMidnight() {
        assertTrue(BrightnessPolicy.isScheduledDay(1200, 360, 1380));
        assertTrue(BrightnessPolicy.isScheduledDay(1200, 360, 359));
        assertFalse(BrightnessPolicy.isScheduledDay(1200, 360, 360));
        assertFalse(BrightnessPolicy.isScheduledDay(1200, 360, 720));
    }
    @Test public void setAutoUsesExplicitDayNightNotStockAuto() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 1140, false);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY, BrightnessPolicy.desiredTopwayMode(cfg, 480));
        assertEquals(BrightnessPolicy.TOPWAY_MODE_NIGHT, BrightnessPolicy.desiredTopwayMode(cfg, 1320));
    }
    @Test public void nextActionChangesOneSemanticVariableAtATime() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.DAY, 9, 4, 420, 1140, false);
        assertEquals(BrightnessPolicy.ActionType.SET_DAY_LEVEL,
                BrightnessPolicy.nextAction(cfg, new BrightnessPolicy.State(true, 0, true, 8, 6, false), 720).type);
        assertEquals(BrightnessPolicy.ActionType.SET_NIGHT_LEVEL,
                BrightnessPolicy.nextAction(cfg, new BrightnessPolicy.State(true, 0, true, 9, 6, false), 720).type);
        BrightnessPolicy.Action third = BrightnessPolicy.nextAction(cfg, new BrightnessPolicy.State(true, 0, true, 9, 4, false), 720);
        assertEquals(BrightnessPolicy.ActionType.SET_MODE, third.type);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY, third.value);
    }
    @Test public void preserveLevelLeavesStockSlotUntouched() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.NIGHT, -1, -1, 420, 1140, false);
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(cfg, new BrightnessPolicy.State(true, 1, true, 8, 6, false), 720);
        assertEquals(BrightnessPolicy.ActionType.SET_MODE, action.type);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_NIGHT, action.value);
    }
    @Test public void levelZeroIsNotAcceptedAsManagedLevel() {
        assertEquals(-1, BrightnessPolicy.sanitiseLevel(0));
        assertEquals(1, BrightnessPolicy.sanitiseLevel(1));
        assertEquals(10, BrightnessPolicy.sanitiseLevel(10));
        assertEquals(-1, BrightnessPolicy.sanitiseLevel(11));
    }
    @Test public void nextTransitionWrapsAtMidnight() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 1140, false);
        assertEquals(60, BrightnessPolicy.minutesUntilNextTransition(cfg, 1080));
        assertEquals(480, BrightnessPolicy.minutesUntilNextTransition(cfg, 1380));
    }
    @Test public void equalTransitionTimesAreInvalid() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 420, false);
        assertFalse(cfg.scheduleValid());
        assertEquals(-1, BrightnessPolicy.minutesUntilNextTransition(cfg, 480));
        assertEquals(BrightnessPolicy.ActionType.NONE,
                BrightnessPolicy.nextAction(cfg, new BrightnessPolicy.State(true, 0, true, 8, 6, false), 480).type);
    }
}
