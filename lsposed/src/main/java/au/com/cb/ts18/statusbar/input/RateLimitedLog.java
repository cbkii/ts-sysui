package au.com.cb.ts18.statusbar.input;

import android.os.SystemClock;
import de.robv.android.xposed.XposedBridge;

final class RateLimitedLog {
    private static final String TAG = "TS18StatusBar: ";
    private static final long INTERVAL_MS = 5000L;
    private static volatile long lastDebugAt;
    private static volatile long lastErrorAt;

    private RateLimitedLog() {}

    static void debug(boolean enabled, String message) {
        if (!enabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastDebugAt < INTERVAL_MS) return;
        lastDebugAt = now;
        XposedBridge.log(TAG + message);
    }

    static void error(String message, Throwable throwable) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastErrorAt < INTERVAL_MS) return;
        lastErrorAt = now;
        XposedBridge.log(TAG + message + ": " + throwable);
    }

    static void always(String message) {
        XposedBridge.log(TAG + message);
    }
}
