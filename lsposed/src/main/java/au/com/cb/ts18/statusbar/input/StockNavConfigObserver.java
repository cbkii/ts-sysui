package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only observation of exact CarSetting navigation configuration surfaces. */
final class StockNavConfigObserver {
    static final String PROPERTY_NAV_POSITION = "persist.navibar.position";
    static final String SETTING_NAV_CONFIG = "navigationbar_config";
    static final String SETTING_SHOW_NAV = "show_navigationbar";
    private static final long POLL_MS = 2500L;
    private static final long INIT_RETRY_MS = 5000L;
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private static Context context;
    private static HandlerThread thread;
    private static Handler worker;
    private static ContentObserver observer;
    private static volatile Snapshot last;
    private static volatile long lastChangeAt;
    private static volatile String lastChangeReason = "baseline-not-observed";
    private static volatile String propertyReadDetail = "not-read";
    private static volatile boolean ready;

    private static final Runnable INITIALISE = StockNavConfigObserver::initialiseOnWorker;
    private static final Runnable POLL = new Runnable() {
        @Override public void run() {
            if (!ready) return;
            observe("poll");
            Handler current = worker;
            if (current != null && ready) current.postDelayed(this, POLL_MS);
        }
    };

    private StockNavConfigObserver() {}

    static void start(Context source) {
        if (source == null || !STARTED.compareAndSet(false, true)) return;
        try {
            Context app = source.getApplicationContext();
            context = app == null ? source : app;
            thread = new HandlerThread("TS18-NavConfigObserve");
            thread.start();
            worker = new Handler(thread.getLooper());
            worker.post(INITIALISE);
        } catch (Throwable t) {
            STARTED.set(false);
            ready = false;
            HandlerThread currentThread = thread;
            thread = null;
            worker = null;
            if (currentThread != null) currentThread.quitSafely();
            DiagnosticJournal.failure("stock-nav-config-observer",
                    "read-only nav configuration observer setup failed", t);
        }
    }

    private static void initialiseOnWorker() {
        if (ready) return;
        Handler currentWorker = worker;
        Context currentContext = context;
        if (currentWorker == null || currentContext == null) return;

        ContentObserver candidate = new ContentObserver(currentWorker) {
            @Override public void onChange(boolean selfChange) {
                observe("settings-system-change");
            }
        };
        try {
            currentContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SETTING_NAV_CONFIG), false, candidate);
            currentContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SETTING_SHOW_NAV), false, candidate);
            observer = candidate;
            observe("initial-baseline");
            ready = true;
            currentWorker.removeCallbacks(INITIALISE);
            currentWorker.removeCallbacks(POLL);
            currentWorker.postDelayed(POLL, POLL_MS);
            DiagnosticJournal.state("stock-nav-config-observer", "READY",
                    "Settings.System + read-only property observation");
        } catch (Throwable t) {
            ready = false;
            observer = null;
            safeUnregister(candidate);
            DiagnosticJournal.failure("stock-nav-config-observer",
                    "read-only nav configuration observer failed to start; retrying", t);
            currentWorker.removeCallbacks(INITIALISE);
            currentWorker.postDelayed(INITIALISE, INIT_RETRY_MS);
        }
    }

    private static void safeUnregister(ContentObserver target) {
        Context current = context;
        if (target == null || current == null) return;
        try {
            current.getContentResolver().unregisterContentObserver(target);
        } catch (Throwable ignored) {
            // A partially registered observer is best-effort cleanup before bounded retry.
        }
    }

    private static void observe(String reason) {
        Context current = context;
        if (current == null) return;
        Snapshot next = new Snapshot(
                readSystemSetting(current, SETTING_NAV_CONFIG),
                readSystemSetting(current, SETTING_SHOW_NAV),
                readProperty(PROPERTY_NAV_POSITION));
        Snapshot previous = last;
        last = next;
        if (previous == null || previous.equals(next)) return;
        lastChangeAt = SystemClock.elapsedRealtime();
        lastChangeReason = reason + ": " + previous.summary() + " -> " + next.summary();
        DiagnosticJournal.record("INFO", "stock-nav-config-change", lastChangeReason);
        new Handler(android.os.Looper.getMainLooper()).post(() ->
                ExactTopwayNavVisibilityMonitor.invalidateForStockConfigChange(lastChangeReason));
    }

    private static String readSystemSetting(Context current, String key) {
        try {
            String value = Settings.System.getString(current.getContentResolver(), key);
            return value == null ? "<unset>" : value;
        } catch (Throwable t) {
            return "<unavailable:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static String readProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);
            Object value = get.invoke(null, key, "");
            propertyReadDetail = "reflection-read-ok";
            String text = value == null ? "" : String.valueOf(value);
            return text.isEmpty() ? "<unset>" : text;
        } catch (Throwable t) {
            propertyReadDetail = "unavailable:" + t.getClass().getSimpleName();
            return "<unavailable>";
        }
    }

    static void appendStatus(Bundle out) {
        if (out == null) return;
        Snapshot snapshot = last;
        out.putString("stock_nav_navigationbar_config",
                snapshot == null ? "<unknown>" : snapshot.navigationbarConfig);
        out.putString("stock_nav_show_navigationbar",
                snapshot == null ? "<unknown>" : snapshot.showNavigationbar);
        out.putString("stock_nav_persist_navibar_position",
                snapshot == null ? "<unknown>" : snapshot.persistNavibarPosition);
        out.putString("stock_nav_property_read_detail", propertyReadDetail);
        out.putLong("stock_nav_config_last_change_at", lastChangeAt);
        out.putString("stock_nav_config_last_change_reason", lastChangeReason);
        out.putBoolean("stock_nav_config_observer_started", STARTED.get());
        out.putBoolean("stock_nav_config_observer_ready", ready);
    }

    static final class Snapshot {
        final String navigationbarConfig;
        final String showNavigationbar;
        final String persistNavibarPosition;

        Snapshot(String navigationbarConfig, String showNavigationbar,
                 String persistNavibarPosition) {
            this.navigationbarConfig = navigationbarConfig;
            this.showNavigationbar = showNavigationbar;
            this.persistNavibarPosition = persistNavibarPosition;
        }

        String summary() {
            return "navigationbar_config=" + navigationbarConfig
                    + ",show_navigationbar=" + showNavigationbar
                    + ",persist.navibar.position=" + persistNavibarPosition;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return same(navigationbarConfig, that.navigationbarConfig)
                    && same(showNavigationbar, that.showNavigationbar)
                    && same(persistNavibarPosition, that.persistNavibarPosition);
        }

        @Override public int hashCode() { return summary().hashCode(); }

        private static boolean same(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
