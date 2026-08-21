package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.content.Intent;
import android.os.ResultReceiver;
import android.provider.Settings;

final class BrightnessConfig {
    static final String POLICY_VERSION = "1";
    static final String MODULE_PACKAGE = "au.com.cb.ts18.statusbar.input";
    static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    static final String CONFIGURE_PERMISSION = MODULE_PACKAGE + ".permission.CONFIGURE_BRIGHTNESS";
    static final String ACTION_APPLY = MODULE_PACKAGE + ".action.APPLY_BRIGHTNESS_CONFIG";
    static final String EXTRA_NONCE = "nonce";
    static final String EXTRA_RESULT_RECEIVER = "result_receiver";
    static final String EXTRA_ENABLED = "enabled";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_DAY_LEVEL = "day_level";
    static final String EXTRA_NIGHT_LEVEL = "night_level";
    static final String EXTRA_DAY_START_MINUTE = "day_start_minute";
    static final String EXTRA_NIGHT_START_MINUTE = "night_start_minute";
    static final String EXTRA_DEBUG = "debug";
    static final String EXTRA_SUCCESS = "success";
    static final String EXTRA_DETAIL = "detail";
    static final int RESULT_REJECTED = 0;
    static final int RESULT_APPLIED = 1;

    static final String PREFIX = "ts18_brightness_";
    static final String KEY_POLICY_VERSION = PREFIX + "policy_version";
    static final String KEY_ENABLED = PREFIX + "enabled";
    static final String KEY_MODE = PREFIX + "mode";
    static final String KEY_DAY_LEVEL = PREFIX + "day_level";
    static final String KEY_NIGHT_LEVEL = PREFIX + "night_level";
    static final String KEY_DAY_START_MINUTE = PREFIX + "day_start_minute";
    static final String KEY_NIGHT_START_MINUTE = PREFIX + "night_start_minute";
    static final String KEY_DEBUG = PREFIX + "debug";

    static final String[] OBSERVED_KEYS = {
            KEY_POLICY_VERSION,
            KEY_ENABLED,
            KEY_MODE,
            KEY_DAY_LEVEL,
            KEY_NIGHT_LEVEL,
            KEY_DAY_START_MINUTE,
            KEY_NIGHT_START_MINUTE,
            KEY_DEBUG
    };

    private BrightnessConfig() {}

    static BrightnessPolicy.Config read(Context context) {
        try {
            String policy = get(context, KEY_POLICY_VERSION);
            if (!POLICY_VERSION.equals(policy == null ? null : policy.trim())) {
                return defaults(false);
            }

            String rawMode = get(context, KEY_MODE);
            BrightnessPolicy.ControlMode mode = rawMode == null
                    ? BrightnessPolicy.ControlMode.AUTO
                    : parseStrictMode(rawMode);
            int dayLevel = readIntStrict(context, KEY_DAY_LEVEL, BrightnessPolicy.PRESERVE_LEVEL);
            int nightLevel = readIntStrict(context, KEY_NIGHT_LEVEL, BrightnessPolicy.PRESERVE_LEVEL);
            int dayStart = readIntStrict(context, KEY_DAY_START_MINUTE, 7 * 60);
            int nightStart = readIntStrict(context, KEY_NIGHT_START_MINUTE, 19 * 60);

            if (!validManagedLevel(dayLevel) || !validManagedLevel(nightLevel)) {
                throw new IllegalArgumentException("persisted brightness level is outside preserve-current/1..10");
            }
            if (!validMinute(dayStart) || !validMinute(nightStart)) {
                throw new IllegalArgumentException("persisted transition minute is outside 0..1439");
            }

            BrightnessPolicy.Config config = new BrightnessPolicy.Config(
                    readBoolean(context, KEY_ENABLED, false),
                    mode,
                    dayLevel,
                    nightLevel,
                    dayStart,
                    nightStart,
                    readBoolean(context, KEY_DEBUG, false));
            if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
                throw new IllegalArgumentException("persisted Set auto transition times are equal");
            }
            return config;
        } catch (Throwable t) {
            android.util.Log.e("TS18Brightness", "configuration invalid/unreadable; failing open", t);
            return defaults(false);
        }
    }

    static BrightnessPolicy.Config defaults(boolean enabled) {
        return new BrightnessPolicy.Config(enabled, BrightnessPolicy.ControlMode.AUTO,
                BrightnessPolicy.PRESERVE_LEVEL, BrightnessPolicy.PRESERVE_LEVEL,
                7 * 60, 19 * 60, false);
    }

    static BrightnessPolicy.Config fromRequest(Intent intent) {
        if (intent == null || !ACTION_APPLY.equals(intent.getAction())) {
            throw new IllegalArgumentException("unexpected configuration action");
        }
        String rawMode = intent.getStringExtra(EXTRA_MODE);
        BrightnessPolicy.ControlMode mode = parseStrictMode(rawMode);
        int dayLevel = intent.getIntExtra(EXTRA_DAY_LEVEL, Integer.MIN_VALUE);
        int nightLevel = intent.getIntExtra(EXTRA_NIGHT_LEVEL, Integer.MIN_VALUE);
        int dayStart = intent.getIntExtra(EXTRA_DAY_START_MINUTE, -1);
        int nightStart = intent.getIntExtra(EXTRA_NIGHT_START_MINUTE, -1);
        if (!validManagedLevel(dayLevel) || !validManagedLevel(nightLevel)) {
            throw new IllegalArgumentException("brightness levels must be preserve-current or 1..10");
        }
        if (!validMinute(dayStart) || !validMinute(nightStart)) {
            throw new IllegalArgumentException("transition minutes must be 0..1439");
        }
        BrightnessPolicy.Config config = new BrightnessPolicy.Config(
                intent.getBooleanExtra(EXTRA_ENABLED, false), mode,
                dayLevel, nightLevel, dayStart, nightStart,
                intent.getBooleanExtra(EXTRA_DEBUG, false));
        if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
            throw new IllegalArgumentException("scheduled Day and Night times must differ");
        }
        return config;
    }

    static ResultReceiver resultReceiver(Intent intent) {
        return intent == null ? null : intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
    }

    /**
     * Called only from the exact-hash-gated injected SystemUI process or from tests.
     * The current exact SystemUI package holds WRITE_SECURE_SETTINGS. Publish the
     * enable bit last so an interrupted update cannot expose a partial policy.
     */
    static void persistFromSystemUi(Context context, BrightnessPolicy.Config config) {
        putRequired(context, KEY_ENABLED, "0");
        putRequired(context, KEY_MODE, config.mode.persisted);
        putRequired(context, KEY_DAY_LEVEL, Integer.toString(config.dayLevel));
        putRequired(context, KEY_NIGHT_LEVEL, Integer.toString(config.nightLevel));
        putRequired(context, KEY_DAY_START_MINUTE, Integer.toString(config.dayStartMinute));
        putRequired(context, KEY_NIGHT_START_MINUTE, Integer.toString(config.nightStartMinute));
        putRequired(context, KEY_DEBUG, config.debug ? "1" : "0");
        putRequired(context, KEY_POLICY_VERSION, POLICY_VERSION);
        putRequired(context, KEY_ENABLED, config.enabled ? "1" : "0");
    }

    private static BrightnessPolicy.ControlMode parseStrictMode(String raw) {
        if (raw != null) {
            for (BrightnessPolicy.ControlMode mode : BrightnessPolicy.ControlMode.values()) {
                if (mode.persisted.equals(raw.trim())) return mode;
            }
        }
        throw new IllegalArgumentException("unknown brightness mode");
    }

    private static boolean validManagedLevel(int value) {
        return value == BrightnessPolicy.PRESERVE_LEVEL
                || (value >= BrightnessPolicy.MIN_LEVEL && value <= BrightnessPolicy.MAX_LEVEL);
    }

    private static boolean validMinute(int value) { return value >= 0 && value < 24 * 60; }

    private static String get(Context context, String key) {
        return Settings.Global.getString(context.getContentResolver(), key);
    }

    private static boolean readBoolean(Context context, String key, boolean fallback) {
        String raw = get(context, key);
        if (raw == null) return fallback;
        String value = raw.trim();
        if ("1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) return true;
        if ("0".equals(value) || "false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int readIntStrict(Context context, String key, int fallback) {
        String raw = get(context, key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid persisted integer for " + key, e);
        }
    }

    private static void putRequired(Context context, String key, String value) {
        if (!Settings.Global.putString(context.getContentResolver(), key, value)) {
            throw new IllegalStateException("Settings.Global rejected " + key);
        }
    }
}
