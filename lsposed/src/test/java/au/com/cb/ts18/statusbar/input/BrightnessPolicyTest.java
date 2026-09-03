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
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 1140, false);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY,
                BrightnessPolicy.desiredTopwayMode(cfg, 480));
        assertEquals(BrightnessPolicy.TOPWAY_MODE_NIGHT,
                BrightnessPolicy.desiredTopwayMode(cfg, 1320));
    }

    @Test public void modeIsReconciledBeforePhysicalLevel() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.DAY, 9, 4, 420, 1140, false);
        BrightnessPolicy.State wrongMode = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO, true, 8, 6, false);
        BrightnessPolicy.Action first = BrightnessPolicy.nextAction(cfg, wrongMode, 720,
                BrightnessLevelMapper.logicalToRaw(9));
        assertEquals(BrightnessPolicy.ActionType.SET_MODE, first.type);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY, first.value);

        BrightnessPolicy.State dayMode = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_DAY, true, 8, 6, false);
        BrightnessPolicy.Action second = BrightnessPolicy.nextAction(cfg, dayMode, 720,
                BrightnessLevelMapper.logicalToRaw(8));
        assertEquals(BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL, second.type);
        assertEquals(BrightnessPolicy.SLOT_DAY, second.selector);
        assertEquals(9, second.value);
    }

    @Test public void fixedModePhysicalLevelDoesNotDependOn516Slots() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.NIGHT, 9, 4, 420, 1140, false);
        BrightnessPolicy.State state = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_NIGHT,
                false, 0, 0, false);
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(cfg, state, 720,
                BrightnessLevelMapper.logicalToRaw(5));
        assertEquals(BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL, action.type);
        assertEquals(BrightnessPolicy.SLOT_NIGHT, action.selector);
        assertEquals(4, action.value);
    }

    @Test public void stockAutoManagedLevelsRequireEffective516Observation() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.AUTO, 9, 4, 420, 1140, false);
        BrightnessPolicy.State unknown = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO,
                false, 0, 0, false);
        BrightnessPolicy.LevelTarget target = BrightnessPolicy.targetPhysicalLevel(
                cfg, unknown, 720);
        assertFalse(target.known);
        assertEquals("stock-auto-effective-state-unknown", target.reason);
        assertEquals(BrightnessPolicy.ActionType.NONE,
                BrightnessPolicy.nextAction(cfg, unknown, 720, 255).type);

        BrightnessPolicy.State night = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO,
                true, 10, 2, true);
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(cfg, night, 720,
                BrightnessLevelMapper.logicalToRaw(9));
        assertEquals(BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL, action.type);
        assertEquals(BrightnessPolicy.SLOT_NIGHT, action.selector);
        assertEquals(4, action.value);
    }

    @Test public void preserveLevelLeavesPhysicalBrightnessUntouched() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.NIGHT, -1, -1, 420, 1140, false);
        BrightnessPolicy.State state = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_NIGHT,
                false, 0, 0, false);
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(cfg, state, 720, 180);
        assertEquals(BrightnessPolicy.ActionType.NONE, action.type);
    }

    @Test public void levelZeroIsNotAcceptedAsManagedLevel() {
        assertEquals(-1, BrightnessPolicy.sanitiseLevel(0));
        assertEquals(1, BrightnessPolicy.sanitiseLevel(1));
        assertEquals(10, BrightnessPolicy.sanitiseLevel(10));
        assertEquals(-1, BrightnessPolicy.sanitiseLevel(11));
    }

    @Test public void nextTransitionWrapsAtMidnight() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 1140, false);
        assertEquals(60, BrightnessPolicy.minutesUntilNextTransition(cfg, 1080));
        assertEquals(480, BrightnessPolicy.minutesUntilNextTransition(cfg, 1380));
    }

    @Test public void equalTransitionTimesAreInvalid() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.SET_AUTO, -1, -1, 420, 420, false);
        assertFalse(cfg.scheduleValid());
        assertEquals(-1, BrightnessPolicy.minutesUntilNextTransition(cfg, 480));
        assertEquals(BrightnessPolicy.ActionType.NONE,
                BrightnessPolicy.nextAction(cfg,
                        new BrightnessPolicy.State(true, 0, true, 8, 6, false),
                        480, 255).type);
    }
}
