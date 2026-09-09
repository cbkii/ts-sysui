package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic-only chronology for SCREEN_BRIGHTNESS changes. Never mutates brightness. */
final class BrightnessEventDiagnostics {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static Context context;
    private static HandlerThread thread;
    private static Handler worker;
    private static ContentObserver observer;
    private static volatile ChangeSnapshot change = new ChangeSnapshot(-1, -1, 0L);
    private static volatile boolean ready;

    private BrightnessEventDiagnostics() {}

    static void start(Context source) {
        if (!BuildConfig.TS18_DIAGNOSTIC || source == null
                || !STARTED.compareAndSet(false, true)) return;
        try {
            Context app = source.getApplicationContext();
            context = app == null ? source : app;
            thread = new HandlerThread("TS18-BrightnessChronology");
            thread.start();
            worker = new Handler(thread.getLooper());
            worker.post(BrightnessEventDiagnostics::initialiseOnWorker);
        } catch (Throwable t) {
            STARTED.set(false);
            HandlerThread currentThread = thread;
            thread = null;
            worker = null;
            if (currentThread != null) currentThread.quitSafely();
            DiagnosticJournal.failure("brightness-chronology",
                    "could not set up diagnostic brightness chronology", t);
        }
    }

    private static void initialiseOnWorker() {
        try {
            int initialRaw = readRaw();
            if (initialRaw >= 0) change = new ChangeSnapshot(-1, initialRaw, 0L);
            observer = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) { observeChange(); }
            };
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false, observer);
            ready = true;
            DiagnosticJournal.state("brightness-chronology", "READY",
                    "diagnostic-only SCREEN_BRIGHTNESS observer");
        } catch (Throwable t) {
            DiagnosticJournal.failure("brightness-chronology",
                    "could not start diagnostic brightness chronology", t);
        }
    }

    private static void observeChange() {
        int raw = readRaw();
        ChangeSnapshot current = change;
        if (raw < 0 || raw == current.lastRaw) return;
        ChangeSnapshot next = new ChangeSnapshot(
                current.lastRaw, raw, SystemClock.elapsedRealtime());
        change = next;
        DiagnosticJournal.record("INFO", "brightness-setting-change",
                "screen_brightness " + next.previousRaw + " -> " + next.lastRaw);
    }

    private static int readRaw() {
        try {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    static void appendStatus(Bundle out) {
        if (out == null) return;
        ChangeSnapshot snapshot = change;
        out.putBoolean("brightness_chronology_active", ready && BuildConfig.TS18_DIAGNOSTIC);
        out.putLong("brightness_setting_last_change_at", snapshot.lastChangeAt);
        out.putInt("brightness_setting_previous_raw", snapshot.previousRaw);
        out.putInt("brightness_setting_changed_raw", snapshot.lastRaw);
        if (snapshot.lastChangeAt <= 0L) {
            out.putString("brightness_setting_correlation", "no-setting-change-observed");
            out.putLong("brightness_setting_correlation_delta_ms", Long.MIN_VALUE);
        } else {
            BrightnessEventAttribution.Result result = BrightnessEventAttribution.classify(
                    snapshot.lastChangeAt, snapshot.lastRaw,
                    out.getLong("brightness_last_physical_write_at", 0L),
                    out.getInt("brightness_requested_screen_raw", -1),
                    out.getLong("brightness_last_stock_write_at", 0L),
                    out.getString("brightness_last_stock_write", "none"),
                    out.getLong("brightness_last_258_callback_at", 0L),
                    out.getLong("brightness_last_516_callback_at", 0L));
            out.putString("brightness_setting_correlation", result.classification);
            out.putLong("brightness_setting_correlation_delta_ms", result.deltaMs);
        }
        out.putString("brightness_setting_correlation_note",
                "bounded temporal correlation only; causal writer identity is not proven");
    }

    private static final class ChangeSnapshot {
        final int previousRaw;
        final int lastRaw;
        final long lastChangeAt;

        ChangeSnapshot(int previousRaw, int lastRaw, long lastChangeAt) {
            this.previousRaw = previousRaw;
            this.lastRaw = lastRaw;
            this.lastChangeAt = lastChangeAt;
        }
    }
}
