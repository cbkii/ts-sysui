package au.com.cb.ts18.statusbar.input;

import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Process-local, bounded structured journal for exact-device development.
 *
 * The journal intentionally contains only module/runtime state. Do not add media
 * titles, user text, file paths outside module/SystemUI paths, or other
 * unrelated sensitive user data.
 */
final class DiagnosticJournal {
    private static final int MAX_ENTRIES = BuildConfig.TS18_DIAGNOSTIC ? 512 : 96;
    private static final int MAX_STATES = 48;
    private static final int MAX_STAGE_CHARS = 64;
    private static final int MAX_DETAIL_CHARS = BuildConfig.TS18_DIAGNOSTIC ? 640 : 256;
    private static final int MAX_SNAPSHOT_CHARS = 96 * 1024;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> ENTRIES = new ArrayDeque<>();
    private static final LinkedHashMap<String, String> STATES = new LinkedHashMap<>();
    private static long sequence;
    private static int dropped;

    private DiagnosticJournal() {}

    static void record(String level, String stage, String detail) {
        String safeLevel = bounded(level, 12, "INFO");
        String safeStage = bounded(stage, MAX_STAGE_CHARS, "unknown");
        String safeDetail = bounded(detail, MAX_DETAIL_CHARS, "");
        long elapsed = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        Thread thread = Thread.currentThread();
        String line = String.format(Locale.ROOT,
                "%06d wall=%d elapsed=%d pid=%d tid=%d thread=%s level=%s stage=%s detail=%s",
                nextSequence(), wall, elapsed, Process.myPid(), Process.myTid(),
                bounded(thread.getName(), 48, "unknown"), safeLevel, safeStage, safeDetail);
        synchronized (LOCK) {
            while (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.removeFirst();
                dropped++;
            }
            ENTRIES.addLast(line);
        }
    }

    static void failure(String stage, String detail, Throwable throwable) {
        String suffix = throwable == null ? ""
                : " throwable=" + throwable.getClass().getName()
                + ":" + bounded(throwable.getMessage(), 240, "");
        record("ERROR", stage, safe(detail) + suffix);
    }

    static void state(String stage, String state, String detail) {
        String safeStage = bounded(stage, MAX_STAGE_CHARS, "unknown");
        String value = bounded(state, 40, "unknown")
                + (detail == null || detail.trim().isEmpty()
                ? "" : " | " + bounded(detail, MAX_DETAIL_CHARS, ""));
        synchronized (LOCK) {
            STATES.remove(safeStage);
            STATES.put(safeStage, value);
            while (STATES.size() > MAX_STATES) {
                String eldest = STATES.keySet().iterator().next();
                STATES.remove(eldest);
            }
        }
        record("STATE", safeStage, value);
    }

    static void appendStatus(Bundle out) {
        if (out == null) return;
        out.putBoolean("diagnostic_build", BuildConfig.TS18_DIAGNOSTIC);
        out.putString("diagnostic_build_kind", BuildConfig.TS18_BUILD_KIND);
        out.putString("diagnostic_version_name", BuildConfig.VERSION_NAME);
        out.putInt("diagnostic_version_code", BuildConfig.VERSION_CODE);
        synchronized (LOCK) {
            out.putInt("diagnostic_journal_entries", ENTRIES.size());
            out.putInt("diagnostic_journal_dropped", dropped);
            out.putString("diagnostic_stage_summary", stageSummaryLocked());
            out.putString("diagnostic_journal", snapshotLocked());
        }
    }

    static String snapshotText() {
        synchronized (LOCK) {
            return snapshotLocked();
        }
    }

    static String stageSummary() {
        synchronized (LOCK) {
            return stageSummaryLocked();
        }
    }

    private static long nextSequence() {
        synchronized (LOCK) {
            return ++sequence;
        }
    }

    private static String stageSummaryLocked() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : STATES.entrySet()) {
            if (out.length() > 0) out.append('\n');
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return boundedSnapshot(out.toString());
    }

    private static String snapshotLocked() {
        List<String> lines = new ArrayList<>(ENTRIES);
        StringBuilder out = new StringBuilder();
        out.append("build=").append(BuildConfig.TS18_BUILD_KIND)
                .append(" version=").append(BuildConfig.VERSION_NAME)
                .append('/').append(BuildConfig.VERSION_CODE)
                .append(" diagnostic=").append(BuildConfig.TS18_DIAGNOSTIC)
                .append(" entries=").append(lines.size())
                .append(" dropped=").append(dropped)
                .append('\n');
        if (!STATES.isEmpty()) {
            out.append("-- stage-state --\n");
            for (Map.Entry<String, String> entry : STATES.entrySet()) {
                out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
        }
        out.append("-- events --\n");
        for (String line : lines) {
            if (out.length() + line.length() + 1 > MAX_SNAPSHOT_CHARS) {
                out.append("[snapshot truncated at ").append(MAX_SNAPSHOT_CHARS)
                        .append(" chars]\n");
                break;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String boundedSnapshot(String value) {
        if (value == null || value.length() <= MAX_SNAPSHOT_CHARS) return value;
        return value.substring(0, MAX_SNAPSHOT_CHARS) + "\n[snapshot truncated]";
    }

    private static String bounded(String value, int max, String fallback) {
        String safe = safe(value);
        if (safe.isEmpty()) safe = fallback == null ? "" : fallback;
        if (safe.length() > max) safe = safe.substring(0, max);
        return safe;
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }
}
