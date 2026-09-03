package au.com.cb.ts18.statusbar.input;

/** Pure policy for the exact TS18 Topway brightness contract. */
final class BrightnessPolicy {
    static final int TOPWAY_MODE_AUTO = 0;
    static final int TOPWAY_MODE_DAY = 1;
    static final int TOPWAY_MODE_NIGHT = 2;
    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 10;
    static final int PRESERVE_LEVEL = -1;
    static final int SLOT_DAY = 0;
    static final int SLOT_NIGHT = 1;

    enum ControlMode {
        AUTO("auto"), DAY("day"), NIGHT("night"), SET_AUTO("set_auto");
        final String persisted;
        ControlMode(String persisted) { this.persisted = persisted; }
        static ControlMode parse(String raw) {
            if (raw != null) {
                String value = raw.trim();
                for (ControlMode mode : values()) {
                    if (mode.persisted.equalsIgnoreCase(value)) return mode;
                }
            }
            return AUTO;
        }
    }

    enum ActionType { NONE, SET_DAY_LEVEL, SET_NIGHT_LEVEL, SET_MODE }

    static final class Config {
        final boolean enabled;
        final ControlMode mode;
        final int dayLevel;
        final int nightLevel;
        final int dayStartMinute;
        final int nightStartMinute;
        final boolean debug;

        Config(boolean enabled, ControlMode mode, int dayLevel, int nightLevel,
               int dayStartMinute, int nightStartMinute, boolean debug) {
            this.enabled = enabled;
            this.mode = mode == null ? ControlMode.AUTO : mode;
            this.dayLevel = sanitiseLevel(dayLevel);
            this.nightLevel = sanitiseLevel(nightLevel);
            this.dayStartMinute = clampMinute(dayStartMinute, 7 * 60);
            this.nightStartMinute = clampMinute(nightStartMinute, 19 * 60);
            this.debug = debug;
        }
        boolean scheduleValid() { return dayStartMinute != nightStartMinute; }
        boolean hasManagedLevel() {
            return dayLevel != PRESERVE_LEVEL || nightLevel != PRESERVE_LEVEL;
        }
    }

    /** Semantic state returned by the exact Topway 258/516 callback contract. */
    static final class State {
        final boolean modeKnown;
        final int topwayMode;
        final boolean levelsKnown;
        final int dayLevel;
        final int nightLevel;
        final boolean effectiveNight;
        State(boolean modeKnown, int topwayMode, boolean levelsKnown,
              int dayLevel, int nightLevel, boolean effectiveNight) {
            this.modeKnown = modeKnown;
            this.topwayMode = topwayMode;
            this.levelsKnown = levelsKnown;
            this.dayLevel = dayLevel;
            this.nightLevel = nightLevel;
            this.effectiveNight = effectiveNight;
        }
    }

    static final class Action {
        static final Action NONE = new Action(ActionType.NONE, -1, -1);
        final ActionType type;
        final int selector;
        final int value;
        private Action(ActionType type, int selector, int value) {
            this.type = type;
            this.selector = selector;
            this.value = value;
        }
        static Action dayLevel(int value) {
            return new Action(ActionType.SET_DAY_LEVEL, SLOT_DAY, requireManagedLevel(value));
        }
        static Action nightLevel(int value) {
            return new Action(ActionType.SET_NIGHT_LEVEL, SLOT_NIGHT, requireManagedLevel(value));
        }
        static Action mode(int value) { return new Action(ActionType.SET_MODE, -1, value); }
        String key() { return type.name() + ':' + selector + ':' + value; }
    }

    private BrightnessPolicy() {}

    static Action nextAction(Config config, State state, int localMinute) {
        if (config == null || state == null || !config.enabled) return Action.NONE;
        if (config.mode == ControlMode.SET_AUTO && !config.scheduleValid()) return Action.NONE;
        if (!state.modeKnown || !state.levelsKnown) return Action.NONE;

        // The exact runtime evidence establishes Topway 516 as the active 0..10
        // Day/Night brightness authority. Reconcile each managed slot first so
        // a subsequent mode transition selects an already-confirmed safe value.
        if (config.dayLevel != PRESERVE_LEVEL && state.dayLevel != config.dayLevel) {
            return Action.dayLevel(config.dayLevel);
        }
        if (config.nightLevel != PRESERVE_LEVEL && state.nightLevel != config.nightLevel) {
            return Action.nightLevel(config.nightLevel);
        }

        int desiredMode = desiredTopwayMode(config, localMinute);
        if (state.topwayMode != desiredMode) return Action.mode(desiredMode);
        return Action.NONE;
    }

    static int desiredTopwayMode(Config config, int localMinute) {
        switch (config.mode) {
            case DAY: return TOPWAY_MODE_DAY;
            case NIGHT: return TOPWAY_MODE_NIGHT;
            case SET_AUTO:
                return isScheduledDay(config.dayStartMinute, config.nightStartMinute,
                        clampMinute(localMinute, 0)) ? TOPWAY_MODE_DAY : TOPWAY_MODE_NIGHT;
            case AUTO:
            default: return TOPWAY_MODE_AUTO;
        }
    }

    static boolean isScheduledDay(int dayStartMinute, int nightStartMinute, int localMinute) {
        int day = clampMinute(dayStartMinute, 7 * 60);
        int night = clampMinute(nightStartMinute, 19 * 60);
        int now = clampMinute(localMinute, 0);
        if (day == night) return false;
        if (day < night) return now >= day && now < night;
        return now >= day || now < night;
    }

    static int minutesUntilNextTransition(Config config, int localMinute) {
        if (config == null || config.mode != ControlMode.SET_AUTO || !config.scheduleValid()) return -1;
        int now = clampMinute(localMinute, 0);
        return Math.min(positiveMinutesUntil(now, config.dayStartMinute),
                positiveMinutesUntil(now, config.nightStartMinute));
    }

    private static int positiveMinutesUntil(int now, int target) {
        int delta = clampMinute(target, 0) - now;
        if (delta <= 0) delta += 24 * 60;
        return delta;
    }

    static int sanitiseLevel(int value) {
        if (value == PRESERVE_LEVEL) return value;
        if (value < MIN_LEVEL || value > MAX_LEVEL) return PRESERVE_LEVEL;
        return value;
    }

    private static int requireManagedLevel(int value) {
        int sanitised = sanitiseLevel(value);
        if (sanitised == PRESERVE_LEVEL) {
            throw new IllegalArgumentException("Topway brightness level must be managed 1..10");
        }
        return sanitised;
    }

    static int clampMinute(int value, int fallback) {
        if (value < 0 || value >= 24 * 60) return fallback;
        return value;
    }
}
