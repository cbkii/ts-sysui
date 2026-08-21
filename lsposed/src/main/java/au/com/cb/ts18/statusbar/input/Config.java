package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

final class Config {
    private static final long CACHE_MS = 2000L;

    private static final String PREFIX = "ts18_statusbar_";
    private static final String KEY_POLICY_VERSION = PREFIX + "policy_version";
    private static final String POLICY_VERSION = "4";
    private static final String KEY_ENABLED = PREFIX + "enabled";
    private static final String KEY_INPUT = PREFIX + "input_enabled";
    private static final String KEY_ADAPTER_MODE = PREFIX + "touch_adapter_mode";
    private static final String KEY_FRACTION = PREFIX + "touch_fraction";
    private static final String KEY_CORNER_GAP = PREFIX + "corner_gap_px";
    private static final String KEY_VISUAL = PREFIX + "visual_enabled";
    private static final String KEY_SCALE = PREFIX + "visual_scale";
    private static final String KEY_RIGHT_INSET = PREFIX + "right_inset_px";
    private static final String KEY_DEBUG = PREFIX + "debug";

    private static volatile Snapshot cached = Snapshot.defaults();
    private static volatile long cachedAt = -CACHE_MS;

    private Config() {}

    static Snapshot get(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - cachedAt < CACHE_MS) return cached;
        synchronized (Config.class) {
            now = SystemClock.elapsedRealtime();
            if (now - cachedAt < CACHE_MS) return cached;
            cached = read(context);
            cachedAt = now;
            return cached;
        }
    }

    private static Snapshot read(Context context) {
        try {
            String policyVersion = Settings.Global.getString(
                    context.getContentResolver(), KEY_POLICY_VERSION);
            if (!POLICY_VERSION.equals(policyVersion == null ? null : policyVersion.trim())) {
                // This generation is deliberately inert until its own configuration tool arms
                // it. Old Settings.Global values cannot silently reactivate new exact hooks.
                return Snapshot.defaults();
            }
            return new Snapshot(
                    readBoolean(context, KEY_ENABLED, false),
                    readBoolean(context, KEY_INPUT, false),
                    AdapterMode.parse(Settings.Global.getString(
                            context.getContentResolver(), KEY_ADAPTER_MODE)),
                    readFloat(context, KEY_FRACTION, 0.20f,
                            TouchStripGeometry.MIN_FRACTION, TouchStripGeometry.MAX_FRACTION),
                    readInt(context, KEY_CORNER_GAP, TouchStripGeometry.MIN_CORNER_GAP_PX,
                            TouchStripGeometry.MIN_CORNER_GAP_PX, 2048),
                    readBoolean(context, KEY_VISUAL, false),
                    readFloat(context, KEY_SCALE, 0.75f, 0.50f, 1.00f),
                    readInt(context, KEY_RIGHT_INSET, -1, -1, 1000),
                    readBoolean(context, KEY_DEBUG, false));
        } catch (Throwable t) {
            RateLimitedLog.error("config-read", "configuration read failed; failing open", t);
            return Snapshot.failOpen();
        }
    }

    private static boolean readBoolean(Context context, String key, boolean fallback) {
        String raw = Settings.Global.getString(context.getContentResolver(), key);
        if (raw == null) return fallback;
        String value = raw.trim();
        if (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")) {
            return true;
        }
        if (value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")) {
            return false;
        }
        return fallback;
    }

    private static float readFloat(Context context, String key, float fallback, float low, float high) {
        String raw = Settings.Global.getString(context.getContentResolver(), key);
        if (raw == null) return fallback;
        try {
            float parsed = Float.parseFloat(raw.trim());
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) return fallback;
            return Math.max(low, Math.min(high, parsed));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int readInt(Context context, String key, int fallback, int low, int high) {
        String raw = Settings.Global.getString(context.getContentResolver(), key);
        if (raw == null) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(low, Math.min(high, parsed));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static final class Snapshot {
        final boolean enabled;
        final boolean inputEnabled;
        final AdapterMode adapterMode;
        final float touchFraction;
        final int cornerGapPx;
        final boolean visualEnabled;
        final float visualScale;
        final int rightInsetOverridePx;
        final boolean debug;

        Snapshot(boolean enabled, boolean inputEnabled, AdapterMode adapterMode,
                 float touchFraction, int cornerGapPx,
                 boolean visualEnabled, float visualScale, int rightInsetOverridePx,
                 boolean debug) {
            this.enabled = enabled;
            this.inputEnabled = inputEnabled;
            this.adapterMode = adapterMode;
            this.touchFraction = touchFraction;
            this.cornerGapPx = cornerGapPx;
            this.visualEnabled = visualEnabled;
            this.visualScale = visualScale;
            this.rightInsetOverridePx = rightInsetOverridePx;
            this.debug = debug;
        }

        static Snapshot defaults() {
            // First load is observation-only. The user must explicitly arm each mutation layer.
            return new Snapshot(false, false, AdapterMode.EXACT,
                    0.20f, TouchStripGeometry.MIN_CORNER_GAP_PX,
                    false, 0.75f, -1, false);
        }

        static Snapshot failOpen() {
            return defaults();
        }
    }

    enum AdapterMode {
        EXACT,
        COMPATIBILITY,
        OFF;

        static AdapterMode parse(String raw) {
            if (raw == null || raw.trim().isEmpty() || "exact".equals(raw.trim())) {
                return EXACT;
            }
            if ("compatibility".equals(raw.trim()) || "compat".equals(raw.trim())) {
                return COMPATIBILITY;
            }
            return OFF;
        }
    }
}
