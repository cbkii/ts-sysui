package au.com.cb.ts18.statusbar.input;

/** Pure policy for the exact TS18 CarSetting-backed brightness contract. */
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

    enum ActionType { NONE, SET_PHYSICAL_LEVEL, SET_MODE }

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

    /** Topway semantic observation. The day/night slots originate from callback 516 only. */
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

    static final class LevelTarget {
        final boolean known;
        final int selector;
        final int logicalLevel;
        final String reason;

        private LevelTarget(boolean known, int selector, int logicalLevel, String reason) {
            this.known = known;
            this.selector = selector;
            this.logicalLevel = logicalLevel;
            this.reason = reason;
        }

        static LevelTarget day(int level) {
            return new LevelTarget(true, SLOT_DAY, level, "day");
        }

        static LevelTarget night(int level) {
            return new LevelTarget(true, SLOT_NIGHT, level, "night");
        }

        static LevelTarget none(String reason) {
            return new LevelTarget(true, -1, PRESERVE_LEVEL, reason);
        }

        static LevelTarget unknown(String reason) {
            return new LevelTarget(false, -1, PRESERVE_LEVEL, reason);
        }

        boolean managed() {
            return known && logicalLevel != PRESERVE_LEVEL;
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
        static Action physicalLevel(int selector, int logicalLevel) {
            if (selector != SLOT_DAY && selector != SLOT_NIGHT) {
                throw new IllegalArgumentException("physical brightness slot must be day or night");
            }
            if (sanitiseLevel(logicalLevel) == PRESERVE_LEVEL) {
                throw new IllegalArgumentException("physical brightness level must be managed 1..10");
            }
            return new Action(ActionType.SET_PHYSICAL_LEVEL, selector, logicalLevel);
        }
        static Action mode(int value) { return new Action(ActionType.SET_MODE, -1, value); }
        int rawBrightness() {
            return type == ActionType.SET_PHYSICAL_LEVEL
                    ? BrightnessLevelMapper.logicalToRaw(value) : -1;
        }
        String key() {
            if (type == ActionType.SET_PHYSICAL_LEVEL) {
                return type.name() + ':' + selector + ':' + value + ":raw=" + rawBrightness();
            }
            return type.name() + ':' + selector + ':' + value;
        }
    }

    private BrightnessPolicy() {}

    static Action nextAction(Config config, State state, int localMinute,
                             int observedScreenBrightnessRaw) {
        if (config == null || state == null || !config.enabled) return Action.NONE;
        if (config.mode == ControlMode.SET_AUTO && !config.scheduleValid()) return Action.NONE;
        if (!state.modeKnown) return Action.NONE;

        int desiredMode = desiredTopwayMode(config, localMinute);
        if (state.topwayMode != desiredMode) return Action.mode(desiredMode);

        LevelTarget target = targetPhysicalLevel(config, state, localMinute);
        if (!target.known || !target.managed() || observedScreenBrightnessRaw < 0) {
            return Action.NONE;
        }
        if (!BrightnessLevelMapper.matchesLogical(target.logicalLevel,
                observedScreenBrightnessRaw)) {
            return Action.physicalLevel(target.selector, target.logicalLevel);
        }
        return Action.NONE;
    }

    static LevelTarget targetPhysicalLevel(Config config, State state, int localMinute) {
        if (config == null || !config.enabled) return LevelTarget.none("disabled");
        switch (config.mode) {
            case DAY:
                return LevelTarget.day(config.dayLevel);
            case NIGHT:
                return LevelTarget.night(config.nightLevel);
            case SET_AUTO:
                if (!config.scheduleValid()) return LevelTarget.unknown("invalid-schedule");
                return isScheduledDay(config.dayStartMinute, config.nightStartMinute,
                        clampMinute(localMinute, 0))
                        ? LevelTarget.day(config.dayLevel)
                        : LevelTarget.night(config.nightLevel);
            case AUTO:
            default:
                if (!config.hasManagedLevel()) return LevelTarget.none("stock-auto-preserve-current");
                // Callback 516 is observation-only here. It supplies the effective
                // stock Day/Night decision, not physical brightness confirmation.
                if (state == null || !state.levelsKnown) {
                    return LevelTarget.unknown("stock-auto-effective-state-unknown");
                }
                return state.effectiveNight
                        ? LevelTarget.night(config.nightLevel)
                        : LevelTarget.day(config.dayLevel);
        }
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

    static int clampMinute(int value, int fallback) {
        if (value < 0 || value >= 24 * 60) return fallback;
        return value;
    }
}
