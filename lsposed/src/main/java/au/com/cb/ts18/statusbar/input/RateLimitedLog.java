package au.com.cb.ts18.statusbar.input;

import android.os.SystemClock;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

final class RateLimitedLog {
    private static final String TAG = "TS18StatusBar: ";
    private static final long INTERVAL_MS = 5000L;
    private static final int MAX_ERROR_STAGES = 16;

    private static final Object ERROR_LOCK = new Object();
    private static final Map<String, Long> lastErrorAt = new LinkedHashMap<>();
    private static final Set<String> tracedStages = new HashSet<>();
    private static volatile long lastDebugAt;

    private RateLimitedLog() {}

    static void debug(boolean enabled, String message) {
        if (!enabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastDebugAt < INTERVAL_MS) return;
        lastDebugAt = now;
        XposedBridge.log(TAG + message);
    }

    static void error(String stage, String message, Throwable throwable) {
        String key = boundedStage(stage);
        long now = SystemClock.elapsedRealtime();
        boolean logLine;
        boolean logTrace;
        synchronized (ERROR_LOCK) {
            Long previous = lastErrorAt.get(key);
            logLine = previous == null || now - previous >= INTERVAL_MS;
            if (logLine) lastErrorAt.put(key, now);
            logTrace = throwable != null && tracedStages.add(key);
            trimErrorStages();
        }
        if (logLine) {
            XposedBridge.log(TAG + key + ": " + message
                    + (throwable == null ? "" : ": " + throwable));
        }
        if (logTrace) {
            // First failure for each bounded stage keeps a real stack trace for diagnosis.
            XposedBridge.log(throwable);
        }
    }

    static void always(String message) {
        XposedBridge.log(TAG + message);
    }

    private static String boundedStage(String stage) {
        if (stage == null || stage.isEmpty()) return "unknown";
        return stage.length() <= 64 ? stage : stage.substring(0, 64);
    }

    private static void trimErrorStages() {
        while (lastErrorAt.size() > MAX_ERROR_STAGES) {
            String eldest = lastErrorAt.keySet().iterator().next();
            lastErrorAt.remove(eldest);
            tracedStages.remove(eldest);
        }
    }
}
