package au.com.cb.ts18.statusbar.input;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

/** Exact-hash-gated, read-only reverse/sleep observer plus explicit diagnostic media calls. */
final class ExactXtServiceObserver {
    interface QualificationCallback { void onResult(boolean success, String detail); }

    private static final long REBIND_MS = 5000L;
    private static final long BIND_TIMEOUT_MS = 10000L;
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context context;
    private static HandlerThread thread;
    private static Handler worker;
    private static ServiceConnection connection;
    private static IBinder remote;
    private static ExactXtServiceBinder.CallbackBinder callbackBinder;
    private static boolean bound;
    private static boolean binding;
    private static boolean callbackRegistered;
    private static long bindStartedAt;
    private static long lastBindAttemptAt;
    private static volatile String identityState = "UNCHECKED";
    private static volatile String identityDetail = "not-checked";
    private static volatile String actualSha256 = "unknown";
    private static volatile String versionName = "unknown";
    private static volatile String bindState = "not-started";
    private static volatile boolean reverseKnown;
    private static volatile int reverseStatus = -1;
    private static volatile boolean reverseLastKnownActive;
    private static volatile long reverseAt;
    private static volatile boolean sleepKnown;
    private static volatile int sleepStatus = -1;
    private static volatile boolean sleepLastKnownActive;
    private static volatile long sleepAt;
    private static volatile String lastQualificationAction = "none";
    private static volatile long lastQualificationAt;
    private static volatile boolean lastQualificationBinderSuccess;
    private static volatile String lastQualificationDetail = "none";
    private static volatile boolean stopped;

    private static final Runnable BIND = ExactXtServiceObserver::bindNow;
    private static final Runnable BIND_TIMEOUT = () -> {
        if (!binding || stopped) return;
        binding = false;
        bindState = "bind-timeout";
        XtServiceFeatureRuntime.recordFailure("bind-timeout",
                new IllegalStateException("CommandService bind timed out"));
        scheduleBind(REBIND_MS);
    };

    private ExactXtServiceObserver() {}

    static void start(Context source) {
        if (source == null || !STARTED.compareAndSet(false, true)) return;
        Context app = source.getApplicationContext();
        context = app == null ? source : app;
        thread = new HandlerThread("TS18-XTObserver");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(ExactXtServiceObserver::initialiseOnWorker);
    }

    private static void initialiseOnWorker() {
        ExactXtServiceContract.Result result = ExactXtServiceContract.verifyInstalled(context);
        actualSha256 = result.actualSha256;
        versionName = result.versionName;
        identityDetail = result.detail;
        identityState = result.supported ? "SUPPORTED" : "UNSUPPORTED";
        DiagnosticJournal.state("xtservice-identity", identityState,
                result.detail + " version=" + result.versionName);
        if (!result.supported || stopped) return;
        bindNow();
    }

    private static void bindNow() {
        if (stopped || !XtServiceFeatureRuntime.isOperational() || bound || binding) return;
        long now = SystemClock.elapsedRealtime();
        if (lastBindAttemptAt > 0L && now - lastBindAttemptAt < REBIND_MS) {
            scheduleBind(REBIND_MS - (now - lastBindAttemptAt));
            return;
        }
        lastBindAttemptAt = now;
        bindStartedAt = now;
        binding = true;
        bindState = "binding";
        ServiceConnection next = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                postWorker(() -> connectedOnWorker(name, service));
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                postWorker(() -> disconnectedOnWorker("disconnected:" + name, false));
            }
            @Override public void onBindingDied(ComponentName name) {
                postWorker(() -> disconnectedOnWorker("binding-died:" + name, true));
            }
            @Override public void onNullBinding(ComponentName name) {
                postWorker(() -> disconnectedOnWorker("null-binding:" + name, true));
            }
        };
        connection = next;
        try {
            boolean accepted = context.bindService(
                    ExactXtServiceContract.bindIntent(), next, Context.BIND_AUTO_CREATE);
            if (!accepted) {
                binding = false;
                connection = null;
                bindState = "bindService-returned-false";
                XtServiceFeatureRuntime.recordFailure("bind-rejected",
                        new IllegalStateException(bindState));
                scheduleBind(REBIND_MS);
                return;
            }
            worker.removeCallbacks(BIND_TIMEOUT);
            worker.postDelayed(BIND_TIMEOUT, BIND_TIMEOUT_MS);
        } catch (Throwable t) {
            binding = false;
            connection = null;
            bindState = "bind-exception:" + t.getClass().getSimpleName();
            XtServiceFeatureRuntime.recordFailure("bind", t);
            scheduleBind(REBIND_MS);
        }
    }

    private static void connectedOnWorker(ComponentName name, IBinder service) {
        if (stopped) return;
        worker.removeCallbacks(BIND_TIMEOUT);
        binding = false;
        bound = service != null;
        remote = service;
        bindState = bound ? "bound:" + name : "connected-null-binder";
        if (!bound) {
            XtServiceFeatureRuntime.recordFailure("connected-null",
                    new IllegalStateException(bindState));
            scheduleBind(REBIND_MS);
            return;
        }
        try {
            callbackBinder = new ExactXtServiceBinder.CallbackBinder(
                    new ExactXtServiceBinder.Listener() {
                        @Override public void onReverseStatus(int status) {
                            postWorker(() -> acceptReverse(status));
                        }
                        @Override public void onSleepStatus(int status) {
                            postWorker(() -> acceptSleep(status));
                        }
                    });
            ExactXtServiceBinder.registerCallback(remote, callbackBinder);
            callbackRegistered = true;
            ExactXtServiceBinder.requestInitialState(remote);
            bindState = "bound-callback-registered-initial-state-requested";
            DiagnosticJournal.state("xtservice-bind", "READY", bindState);
        } catch (Throwable t) {
            callbackRegistered = false;
            XtServiceFeatureRuntime.recordFailure("register-query", unwrap(t));
            disconnectedOnWorker("register-query-failed", false);
        }
    }

    private static void disconnectedOnWorker(String reason, boolean countFailure) {
        worker.removeCallbacks(BIND_TIMEOUT);
        ServiceConnection oldConnection = connection;
        connection = null;
        if (oldConnection != null && context != null) {
            try { context.unbindService(oldConnection); }
            catch (Throwable ignored) { }
        }
        remote = null;
        bound = false;
        binding = false;
        callbackRegistered = false;
        callbackBinder = null;
        bindState = reason;
        reverseKnown = false;
        sleepKnown = false;
        requestVehicleReevaluation();
        if (countFailure) {
            XtServiceFeatureRuntime.recordFailure("connection", new RemoteException(reason));
        }
        if (!stopped && XtServiceFeatureRuntime.isOperational()
                && "SUPPORTED".equals(identityState)) scheduleBind(REBIND_MS);
    }

    private static void acceptReverse(int status) {
        reverseAt = SystemClock.elapsedRealtime();
        if (status == 0 || status == 1) {
            reverseKnown = true;
            reverseStatus = status;
            reverseLastKnownActive = status == 1;
        } else {
            reverseKnown = false;
            reverseStatus = status;
        }
        DiagnosticJournal.record("INFO", "xtservice-reverse",
                "known=" + reverseKnown + " status=" + status);
        requestVehicleReevaluation();
    }

    private static void acceptSleep(int status) {
        sleepAt = SystemClock.elapsedRealtime();
        if (status == 0 || status == 1) {
            sleepKnown = true;
            sleepStatus = status;
            sleepLastKnownActive = status == 1;
        } else {
            sleepKnown = false;
            sleepStatus = status;
        }
        DiagnosticJournal.record("INFO", "xtservice-sleep",
                "known=" + sleepKnown + " status=" + status);
        requestVehicleReevaluation();
    }

    static VehicleStatePolicy.Decision vehicleDecision() {
        return VehicleStatePolicy.evaluate(reverseKnown, reverseStatus,
                reverseLastKnownActive, sleepKnown, sleepStatus, sleepLastKnownActive);
    }

    static void qualifyMedia(String action, QualificationCallback callback) {
        Handler current = worker;
        if (current == null) {
            finishQualification(callback, false, "XTService observer not started");
            return;
        }
        current.post(() -> qualifyOnWorker(action, callback));
    }

    private static void qualifyOnWorker(String action, QualificationCallback callback) {
        long now = SystemClock.elapsedRealtime();
        lastQualificationAction = action == null ? "null" : action;
        lastQualificationAt = now;
        lastQualificationBinderSuccess = false;
        if (!BuildConfig.TS18_DIAGNOSTIC) {
            finishQualification(callback, false, "qualification is diagnostic-build-only");
            return;
        }
        if (!ExactXtServiceBinder.isQualificationAction(action)) {
            finishQualification(callback, false, "unsupported qualification action");
            return;
        }
        if (!ExactSystemUiIdentity.isSupported() || !"SUPPORTED".equals(identityState)) {
            finishQualification(callback, false, "exact SystemUI/XTService identity not supported");
            return;
        }
        IBinder current = remote;
        if (!bound || !callbackRegistered || current == null || !current.isBinderAlive()
                || !XtServiceFeatureRuntime.isOperational()) {
            finishQualification(callback, false, "XTService not ready for explicit qualification");
            return;
        }
        try {
            ExactXtServiceBinder.qualifyMedia(current, action);
            lastQualificationBinderSuccess = true;
            finishQualification(callback, true,
                    "Binder call returned without exception; playback effect remains unproven");
        } catch (Throwable t) {
            finishQualification(callback, false,
                    "Binder call failed: " + unwrap(t).getClass().getSimpleName());
        }
    }

    private static void finishQualification(QualificationCallback callback,
                                            boolean success, String detail) {
        lastQualificationDetail = detail;
        DiagnosticJournal.record(success ? "INFO" : "WARN", "xtservice-media-qualification",
                "action=" + lastQualificationAction + " binderSuccess=" + success
                        + " detail=" + detail);
        if (callback != null) MAIN.post(() -> callback.onResult(success, detail));
    }

    static void appendStatus(Bundle out) {
        if (out == null) return;
        VehicleStatePolicy.Decision decision = vehicleDecision();
        out.putString("xtservice_expected_sha256", ExactXtServiceContract.EXPECTED_APK_SHA256);
        out.putString("xtservice_actual_sha256", actualSha256);
        out.putString("xtservice_version", versionName);
        out.putString("xtservice_identity_state", identityState);
        out.putString("xtservice_identity_detail", identityDetail);
        out.putString("xtservice_bind_state", bindState);
        out.putBoolean("xtservice_bound", bound);
        out.putBoolean("xtservice_callback_registered", callbackRegistered);
        out.putBoolean("xtservice_reverse_known", reverseKnown);
        out.putInt("xtservice_reverse_status", reverseStatus);
        out.putLong("xtservice_reverse_at", reverseAt);
        out.putBoolean("xtservice_sleep_known", sleepKnown);
        out.putInt("xtservice_sleep_status", sleepStatus);
        out.putLong("xtservice_sleep_at", sleepAt);
        out.putBoolean("xtservice_vehicle_veto", !decision.allowNavMedia);
        out.putString("xtservice_vehicle_policy", decision.reason);
        out.putBoolean("xtservice_breaker_open", XtServiceFeatureRuntime.isBreakerOpen());
        out.putInt("xtservice_failure_count", XtServiceFeatureRuntime.failureCount());
        out.putString("xtservice_last_qualification_action", lastQualificationAction);
        out.putLong("xtservice_last_qualification_at", lastQualificationAt);
        out.putBoolean("xtservice_last_qualification_binder_success", lastQualificationBinderSuccess);
        out.putString("xtservice_last_qualification_detail", lastQualificationDetail);
    }

    static void stopForProcess() {
        stopped = true;
        Handler current = worker;
        if (current != null) current.post(ExactXtServiceObserver::cleanupOnWorker);
    }

    private static void cleanupOnWorker() {
        worker.removeCallbacksAndMessages(null);
        IBinder currentRemote = remote;
        ExactXtServiceBinder.CallbackBinder currentCallback = callbackBinder;
        if (currentRemote != null && currentCallback != null && callbackRegistered) {
            try { ExactXtServiceBinder.unregisterCallback(currentRemote, currentCallback); }
            catch (Throwable ignored) { }
        }
        ServiceConnection currentConnection = connection;
        connection = null;
        if (currentConnection != null && context != null) {
            try { context.unbindService(currentConnection); }
            catch (Throwable ignored) { }
        }
        remote = null;
        bound = false;
        binding = false;
        callbackRegistered = false;
        callbackBinder = null;
        reverseKnown = false;
        sleepKnown = false;
        requestVehicleReevaluation();
        if (thread != null) thread.quitSafely();
    }

    private static void scheduleBind(long delay) {
        Handler current = worker;
        if (current == null || stopped || !XtServiceFeatureRuntime.isOperational()) return;
        current.removeCallbacks(BIND);
        current.postDelayed(BIND, Math.max(250L, delay));
    }

    private static void requestVehicleReevaluation() {
        MAIN.post(ExactTopwayNavVisibilityMonitor::requestVehicleStateReevaluation);
    }

    private static void postWorker(Runnable runnable) {
        Handler current = worker;
        if (current != null && !stopped) current.post(runnable);
    }

    private static Throwable unwrap(Throwable t) {
        return t != null && t.getCause() != null ? t.getCause() : t;
    }
}
