package au.com.cb.ts18.statusbar.input;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central runtime logging policy.
 *
 * Release builds remain conservative. Diagnostic/debug builds retain bounded
 * high-detail logs in both Android logcat and the LSPosed/Xposed log while also
 * mirroring structured events into DiagnosticJournal for in-app export.
 */
final class RateLimitedLog {
    static final String ANDROID_TAG = "TS18SysUI";
    private static final String XPOSED_PREFIX = "TS18StatusBar: ";
    private static final long RELEASE_INTERVAL_MS = 5000L;
    private static final long DIAGNOSTIC_INTERVAL_MS = 250L;
    private static final int MAX_ERROR_STAGES = 32;
    private static final int MAX_DEBUG_STAGES = 48;
    private static final int MAX_DIAGNOSTIC_TRACES_PER_STAGE = 8;

    private static final Object LOCK = new Object();
    private static final Map<String, Long> lastErrorAt = new LinkedHashMap<>();
    private static final Map<String, Long> lastDebugAt = new LinkedHashMap<>();
    private static final Map<String, Integer> traceCounts = new LinkedHashMap<>();
    private static final Set<String> releaseTracedStages = new HashSet<>();
    private static volatile boolean xposedSinkResolved;
    private static volatile Method xposedStringLog;
    private static volatile Method xposedThrowableLog;

    private RateLimitedLog() {}

    static void debug(boolean enabled, String message) {
        debug(enabled, inferredStage(message), message);
    }

    static void debug(boolean enabled, String stage, String message) {
        if (!enabled && !BuildConfig.TS18_DIAGNOSTIC) return;
        String key = boundedStage(stage);
        long now = SystemClock.elapsedRealtime();
        long interval = BuildConfig.TS18_DIAGNOSTIC
                ? DIAGNOSTIC_INTERVAL_MS : RELEASE_INTERVAL_MS;
        synchronized (LOCK) {
            Long previous = lastDebugAt.get(key);
            if (previous != null && now - previous < interval) return;
            lastDebugAt.put(key, now);
            trimMap(lastDebugAt, MAX_DEBUG_STAGES);
        }
        DiagnosticJournal.record("DEBUG", key, message);
        emitLine(Log.DEBUG, key, message);
    }

    static void event(String stage, String message) {
        String key = boundedStage(stage);
        DiagnosticJournal.record("INFO", key, message);
        emitLine(Log.INFO, key, message);
    }

    static void error(String stage, String message, Throwable throwable) {
        String key = boundedStage(stage);
        long now = SystemClock.elapsedRealtime();
        long interval = BuildConfig.TS18_DIAGNOSTIC
                ? DIAGNOSTIC_INTERVAL_MS : RELEASE_INTERVAL_MS;
        boolean logLine;
        boolean logTrace;
        synchronized (LOCK) {
            Long previous = lastErrorAt.get(key);
            logLine = previous == null || now - previous >= interval;
            if (logLine) lastErrorAt.put(key, now);

            if (BuildConfig.TS18_DIAGNOSTIC) {
                int count = traceCounts.containsKey(key) ? traceCounts.get(key) : 0;
                logTrace = throwable != null && logLine
                        && count < MAX_DIAGNOSTIC_TRACES_PER_STAGE;
                if (logTrace) traceCounts.put(key, count + 1);
            } else {
                logTrace = throwable != null && logLine && releaseTracedStages.add(key);
            }
            trimMap(lastErrorAt, MAX_ERROR_STAGES);
            trimMap(traceCounts, MAX_ERROR_STAGES);
            while (releaseTracedStages.size() > MAX_ERROR_STAGES) {
                String first = releaseTracedStages.iterator().next();
                releaseTracedStages.remove(first);
            }
        }

        if (!logLine) return;
        DiagnosticJournal.failure(key, message, throwable);
        emitLine(Log.ERROR, key, message
                + (throwable == null ? "" : ": " + throwable));
        if (logTrace) emitThrowable(key, throwable);
    }

    static void always(String message) {
        event("general", message);
    }

    private static void emitLine(int priority, String stage, String message) {
        String line = stage + ": " + safe(message);
        try {
            Log.println(priority, ANDROID_TAG, line);
        } catch (Throwable ignored) {
            // Android logging must never become runtime authority.
        }
        emitXposedString(XPOSED_PREFIX + line);
    }

    private static void emitThrowable(String stage, Throwable throwable) {
        if (throwable == null) return;
        try {
            Log.e(ANDROID_TAG, stage, throwable);
        } catch (Throwable ignored) {
        }
        emitXposedThrowable(throwable);
    }

    private static void emitXposedString(String value) {
        resolveXposedSink();
        Method method = xposedStringLog;
        if (method == null) return;
        try {
            method.invoke(null, value);
        } catch (Throwable ignored) {
            // Logging must never become runtime authority.
        }
    }

    private static void emitXposedThrowable(Throwable throwable) {
        if (throwable == null) return;
        resolveXposedSink();
        Method method = xposedThrowableLog;
        if (method == null) return;
        try {
            method.invoke(null, throwable);
        } catch (Throwable ignored) {
        }
    }

    private static void resolveXposedSink() {
        if (xposedSinkResolved) return;
        synchronized (LOCK) {
            if (xposedSinkResolved) return;
            try {
                Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
                xposedStringLog = bridge.getDeclaredMethod("log", String.class);
                xposedThrowableLog = bridge.getDeclaredMethod("log", Throwable.class);
                xposedStringLog.setAccessible(true);
                xposedThrowableLog.setAccessible(true);
            } catch (Throwable ignored) {
                xposedStringLog = null;
                xposedThrowableLog = null;
            }
            xposedSinkResolved = true;
        }
    }

    private static String inferredStage(String message) {
        String value = safe(message);
        if (value.isEmpty()) return "debug";
        int boundary = value.length();
        int colon = value.indexOf(':');
        int space = value.indexOf(' ');
        if (colon > 0) boundary = Math.min(boundary, colon);
        if (space > 0) boundary = Math.min(boundary, space);
        boundary = Math.max(1, Math.min(boundary, 32));
        return "debug-" + value.substring(0, boundary);
    }

    private static String boundedStage(String stage) {
        String value = safe(stage);
        if (value.isEmpty()) return "unknown";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    private static <T> void trimMap(Map<String, T> map, int maximum) {
        while (map.size() > maximum) {
            String eldest = map.keySet().iterator().next();
            map.remove(eldest);
        }
    }
}
