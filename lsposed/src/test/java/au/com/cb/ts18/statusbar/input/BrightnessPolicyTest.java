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

    @Test public void managed516SlotsAreConfirmedBeforeModeChange() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.DAY, 9, 4, 420, 1140, false);
        BrightnessPolicy.State initial = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO, true, 8, 6, false);
        BrightnessPolicy.Action first = BrightnessPolicy.nextAction(cfg, initial, 720);
        assertEquals(BrightnessPolicy.ActionType.SET_DAY_LEVEL, first.type);
        assertEquals(BrightnessPolicy.SLOT_DAY, first.selector);
        assertEquals(9, first.value);

        BrightnessPolicy.State dayAligned = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO, true, 9, 6, false);
        BrightnessPolicy.Action second = BrightnessPolicy.nextAction(cfg, dayAligned, 720);
        assertEquals(BrightnessPolicy.ActionType.SET_NIGHT_LEVEL, second.type);
        assertEquals(4, second.value);

        BrightnessPolicy.State levelsAligned = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO, true, 9, 4, false);
        BrightnessPolicy.Action third = BrightnessPolicy.nextAction(cfg, levelsAligned, 720);
        assertEquals(BrightnessPolicy.ActionType.SET_MODE, third.type);
        assertEquals(BrightnessPolicy.TOPWAY_MODE_DAY, third.value);
    }

    @Test public void preserveLevelsLeaves516SlotsUntouched() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.NIGHT, -1, -1, 420, 1140, false);
        BrightnessPolicy.State state = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_NIGHT, true, 8, 6, true);
        assertEquals(BrightnessPolicy.ActionType.NONE,
                BrightnessPolicy.nextAction(cfg, state, 720).type);
    }

    @Test public void managedLevelsRequireKnown516State() {
        BrightnessPolicy.Config cfg = new BrightnessPolicy.Config(true,
                BrightnessPolicy.ControlMode.AUTO, 9, 4, 420, 1140, false);
        BrightnessPolicy.State unknown = new BrightnessPolicy.State(
                true, BrightnessPolicy.TOPWAY_MODE_AUTO, false, 0, 0, false);
        assertEquals(BrightnessPolicy.ActionType.NONE,
                BrightnessPolicy.nextAction(cfg, unknown, 720).type);
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
                        new BrightnessPolicy.State(true, 0, true, 8, 6, false), 480).type);
    }

    @Test public void configurationReportsManagedLevelPresence() {
        assertFalse(new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.AUTO,
                -1, -1, 420, 1140, false).hasManagedLevel());
        assertTrue(new BrightnessPolicy.Config(true, BrightnessPolicy.ControlMode.AUTO,
                8, -1, 420, 1140, false).hasManagedLevel());
    }
}
