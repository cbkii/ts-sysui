package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.List;

final class NavConfig {
    static final int MIN_TOUCH_DP_FLOOR = 48;
    static final int DEFAULT_TOUCH_DP = 56;
    static final int MAX_TOUCH_DP = 96;

    private static final long CACHE_MS = 2000L;
    private static final String PREFIX = "ts18_statusbar_nav_";
    private static final String KEY_POLICY_VERSION = PREFIX + "policy_version";
    private static final String POLICY_VERSION = "1";
    private static final String KEY_ENABLED = PREFIX + "enabled";
    private static final String KEY_PROBE = PREFIX + "probe_enabled";
    private static final String KEY_ACTIONS = PREFIX + "actions";
    private static final String KEY_MIN_TOUCH_DP = PREFIX + "min_touch_dp";
    private static final String KEY_DEBUG = PREFIX + "debug";

    private static volatile Snapshot cached = Snapshot.defaults();
    private static volatile long cachedAt = -CACHE_MS;

    private NavConfig() {}

    static Snapshot get(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - cachedAt < CACHE_MS) return cached;
        synchronized (NavConfig.class) {
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
                return Snapshot.defaults();
            }

            String rawActions = Settings.Global.getString(
                    context.getContentResolver(), KEY_ACTIONS);
            return new Snapshot(
                    readBoolean(context, KEY_ENABLED, false),
                    readBoolean(context, KEY_PROBE, false),
                    NavAction.parseConfigured(rawActions),
                    readInt(context, KEY_MIN_TOUCH_DP, DEFAULT_TOUCH_DP,
                            MIN_TOUCH_DP_FLOOR, MAX_TOUCH_DP),
                    readBoolean(context, KEY_DEBUG, false));
        } catch (Throwable t) {
            RateLimitedLog.error("nav-config-read",
                    "right-nav configuration read failed; failing open", t);
            return Snapshot.defaults();
        }
    }

    private static boolean readBoolean(Context context, String key, boolean fallback) {
        String raw = Settings.Global.getString(context.getContentResolver(), key);
        if (raw == null) return fallback;
        String value = raw.trim();
        if (value.equals("1") || value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("on")) return true;
        if (value.equals("0") || value.equalsIgnoreCase("false")
                || value.equalsIgnoreCase("off")) return false;
        return fallback;
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
        final boolean probeEnabled;
        final List<NavAction> actions;
        final int minTouchDp;
        final boolean debug;

        Snapshot(boolean enabled, boolean probeEnabled, List<NavAction> actions,
                 int minTouchDp, boolean debug) {
            this.enabled = enabled;
            this.probeEnabled = probeEnabled;
            this.actions = actions;
            this.minTouchDp = minTouchDp;
            this.debug = debug;
        }

        static Snapshot defaults() {
            return new Snapshot(false, false, NavAction.defaults(), DEFAULT_TOUCH_DP, false);
        }
    }
}
