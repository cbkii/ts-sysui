package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

final class Config {
    private static final long CACHE_MS = 2000L;

    private static final String PREFIX = "ts18_statusbar_";
    private static final String KEY_ENABLED = PREFIX + "enabled";
    private static final String KEY_INPUT = PREFIX + "input_enabled";
    private static final String KEY_FRACTION = PREFIX + "touch_fraction";
    private static final String KEY_CORNER_GAP = PREFIX + "corner_gap_px";
    private static final String KEY_VISUAL = PREFIX + "visual_enabled";
    private static final String KEY_SCALE = PREFIX + "visual_scale";
    private static final String KEY_RIGHT_INSET = PREFIX + "right_inset_px";
    private static final String KEY_NORMALISE = PREFIX + "window_height_normalise";
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
            return new Snapshot(
                    readBoolean(context, KEY_ENABLED, true),
                    readBoolean(context, KEY_INPUT, true),
                    readFloat(context, KEY_FRACTION, 0.20f,
                            TouchStripGeometry.MIN_FRACTION, TouchStripGeometry.MAX_FRACTION),
                    readInt(context, KEY_CORNER_GAP, TouchStripGeometry.MIN_CORNER_GAP_PX,
                            TouchStripGeometry.MIN_CORNER_GAP_PX, 2048),
                    readBoolean(context, KEY_VISUAL, true),
                    readFloat(context, KEY_SCALE, 0.75f, 0.50f, 1.00f),
                    readInt(context, KEY_RIGHT_INSET, -1, -1, 1000),
                    readBoolean(context, KEY_NORMALISE, true),
                    readBoolean(context, KEY_DEBUG, false));
        } catch (Throwable t) {
            RateLimitedLog.error("config read failed; failing open", t);
            return Snapshot.failOpen();
        }
    }

    private static boolean readBoolean(Context context, String key, boolean fallback) {
        String raw = Settings.Global.getString(context.getContentResolver(), key);
        if (raw == null) return fallback;
        return !(raw.equals("0") || raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off"));
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
        final float touchFraction;
        final int cornerGapPx;
        final boolean visualEnabled;
        final float visualScale;
        final int rightInsetOverridePx;
        final boolean normaliseWindowHeight;
        final boolean debug;

        Snapshot(boolean enabled, boolean inputEnabled, float touchFraction, int cornerGapPx,
                 boolean visualEnabled, float visualScale, int rightInsetOverridePx,
                 boolean normaliseWindowHeight, boolean debug) {
            this.enabled = enabled;
            this.inputEnabled = inputEnabled;
            this.touchFraction = touchFraction;
            this.cornerGapPx = cornerGapPx;
            this.visualEnabled = visualEnabled;
            this.visualScale = visualScale;
            this.rightInsetOverridePx = rightInsetOverridePx;
            this.normaliseWindowHeight = normaliseWindowHeight;
            this.debug = debug;
        }

        static Snapshot defaults() {
            return new Snapshot(true, true, 0.20f, TouchStripGeometry.MIN_CORNER_GAP_PX,
                    true, 0.75f, -1, true, false);
        }

        static Snapshot failOpen() {
            return new Snapshot(false, false, 0.20f, TouchStripGeometry.MIN_CORNER_GAP_PX,
                    false, 0.75f, -1, false, false);
        }
    }
}
