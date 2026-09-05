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
    private static final Object VEHICLE_LOCK = new Object();

    private static Context context;
    private static HandlerThread thread;
    private static Handler worker;
    private static ServiceConnection connection;
    private static IBinder remote;
    private static ExactXtServiceBinder.CallbackBinder callbackBinder;
    private static boolean bound;
    private static boolean binding;
    private static boolean callbackRegistered;
    private static long lastBindAttemptAt;
    private static long nextBindingGeneration;
    private static long activeCallbackGeneration;
    private static volatile String identityState = "UNCHECKED";
    private static volatile String identityDetail = "not-checked";
    private static volatile String actualSha256 = "unknown";
    private static volatile String versionName = "unknown";
    private static volatile String bindState = "not-started";
    private static boolean reverseKnown;
    private static int reverseStatus = -1;
    private static boolean reverseLastKnownActive;
    private static long reverseAt;
    private static boolean sleepKnown;
    private static int sleepStatus = -1;
    private static boolean sleepLastKnownActive;
    private static long sleepAt;
    private static boolean vehicleStateUnsafe;
    private static String vehicleStateUnsafeReason = "none";
    private static volatile String lastQualificationAction = "none";
    private static volatile long lastQualificationAt;
    private static volatile boolean lastQualificationBinderSuccess;
    private static volatile String lastQualificationDetail = "none";
    private static volatile boolean stopped;

    private static final Runnable BIND = ExactXtServiceObserver::bindNow;
    private static final Runnable BIND_TIMEOUT = () -> {
        if (!binding || stopped) return;
        ServiceConnection timedOut = connection;
        connection = null;
        binding = false;
        bound = false;
        remote = null;
        callbackRegistered = false;
        callbackBinder = null;
        activeCallbackGeneration = 0L;
        bindState = "bind-timeout";
        safeUnbind(timedOut, "bind-timeout");
        XtServiceFeatureRuntime.recordFailure("bind-timeout",
                new IllegalStateException("CommandService bind timed out"));
        scheduleBind(REBIND_MS);
    };

    private ExactXtServiceObserver() {}

    static void start(Context source) {
        if (source == null || !STARTED.compareAndSet(false, true)) return;
        try {
            Context app = source.getApplicationContext();
            context = app == null ? source : app;
            thread = new HandlerThread("TS18-XTObserver");
            thread.start();
            worker = new Handler(thread.getLooper());
            worker.post(ExactXtServiceObserver::initialiseOnWorker);
        } catch (Throwable t) {
            STARTED.set(false);
            stopped = true;
            HandlerThread currentThread = thread;
            thread = null;
            worker = null;
            if (currentThread != null) currentThread.quitSafely();
            DiagnosticJournal.failure("xtservice-observer",
                    "XTService observer setup failed", t);
        }
    }

    private static void initialiseOnWorker() {
        bindNow();
    }

    private static boolean refreshIdentityForBind() {
        ExactXtServiceContract.Result result = ExactXtServiceContract.verifyInstalled(context);
        actualSha256 = result.actualSha256;
        versionName = result.versionName;
        identityDetail = result.detail;
        identityState = result.supported ? "SUPPORTED" : "UNSUPPORTED";
        DiagnosticJournal.state("xtservice-identity", identityState,
                result.detail + " version=" + result.versionName);
        if (result.supported) return true;

        bindState = "identity-blocked:" + result.detail;
        DiagnosticJournal.state("xtservice-bind", "STOP", bindState);
        stopForProcess();
        return false;
    }

    private static void bindNow() {
        if (stopped || !XtServiceFeatureRuntime.isOperational() || bound || binding) return;
        long now = SystemClock.elapsedRealtime();
        if (lastBindAttemptAt > 0L && now - lastBindAttemptAt < REBIND_MS) {
            scheduleBind(REBIND_MS - (now - lastBindAttemptAt));
            return;
        }
        if (!refreshIdentityForBind() || stopped) return;

        lastBindAttemptAt = now;
        long generation = ++nextBindingGeneration;
        binding = true;
        bindState = "binding";
        ServiceConnection next = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                ServiceConnection source = this;
                postWorker(() -> connectedOnWorker(source, generation, name, service));
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                ServiceConnection source = this;
                postWorker(() -> disconnectedOnWorker(source,
                        "disconnected:" + name, false));
            }
            @Override public void onBindingDied(ComponentName name) {
                ServiceConnection source = this;
                postWorker(() -> disconnectedOnWorker(source,
                        "binding-died:" + name, true));
            }
            @Override public void onNullBinding(ComponentName name) {
                ServiceConnection source = this;
                postWorker(() -> disconnectedOnWorker(source,
                        "null-binding:" + name, true));
            }
        };
        connection = next;
        try {
            boolean accepted = context.bindService(
                    ExactXtServiceContract.bindIntent(), next, Context.BIND_AUTO_CREATE);
            if (!accepted) {
                binding = false;
                if (connection == next) connection = null;
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
            if (connection == next) connection = null;
            safeUnbind(next, "bind-exception");
            bindState = "bind-exception:" + t.getClass().getSimpleName();
            XtServiceFeatureRuntime.recordFailure("bind", t);
            scheduleBind(REBIND_MS);
        }
    }

    private static void connectedOnWorker(ServiceConnection source, long generation,
                                          ComponentName name, IBinder service) {
        if (stopped || source != connection || generation != nextBindingGeneration) {
            safeUnbind(source, "stale-service-connected-callback");
            return;
        }
        worker.removeCallbacks(BIND_TIMEOUT);
        binding = false;
        bound = service != null;
        remote = service;
        bindState = bound ? "bound:" + name : "connected-null-binder";
        if (!bound) {
            XtServiceFeatureRuntime.recordFailure("connected-null",
                    new IllegalStateException(bindState));
            disconnectedOnWorker(source, bindState, false);
            return;
        }
        try {
            activeCallbackGeneration = generation;
            callbackBinder = new ExactXtServiceBinder.CallbackBinder(
                    new ExactXtServiceBinder.Listener() {
                        @Override public void onReverseStatus(int status) {
                            postWorker(() -> acceptReverse(generation, status));
                        }
                        @Override public void onSleepStatus(int status) {
                            postWorker(() -> acceptSleep(generation, status));
                        }
                    });
            ExactXtServiceBinder.registerCallback(remote, callbackBinder);
            callbackRegistered = true;
            ExactXtServiceBinder.requestInitialState(remote);
            bindState = "bound-callback-registered-initial-state-requested";
            DiagnosticJournal.state("xtservice-bind", "READY", bindState);
        } catch (Throwable t) {
            XtServiceFeatureRuntime.recordFailure("register-query", unwrap(t));
            disconnectedOnWorker(source, "register-query-failed", false);
        }
    }

    private static void disconnectedOnWorker(ServiceConnection source,
                                             String reason, boolean countFailure) {
        if (source != null && source != connection) {
            safeUnbind(source, "stale-service-disconnect-callback");
            return;
        }
        Handler currentWorker = worker;
        if (currentWorker != null) currentWorker.removeCallbacks(BIND_TIMEOUT);
        activeCallbackGeneration = 0L;
        unregisterCallbackBestEffort();
        ServiceConnection oldConnection = connection;
        connection = null;
        safeUnbind(oldConnection, reason);
        remote = null;
        bound = false;
        binding = false;
        callbackRegistered = false;
        callbackBinder = null;
        bindState = reason;
        synchronized (VEHICLE_LOCK) {
            reverseKnown = false;
            sleepKnown = false;
        }
        requestVehicleReevaluation();
        if (countFailure) {
            XtServiceFeatureRuntime.recordFailure("connection", new RemoteException(reason));
        }
        if (!stopped && XtServiceFeatureRuntime.isOperational()
                && "SUPPORTED".equals(identityState)) scheduleBind(REBIND_MS);
    }

    private static boolean currentCallbackGeneration(long generation, String callback) {
        if (!stopped && callbackRegistered && generation == activeCallbackGeneration) return true;
        DiagnosticJournal.record("DEBUG", "xtservice-stale-callback",
                "ignored " + callback + " generation=" + generation
                        + " active=" + activeCallbackGeneration);
        return false;
    }

    private static void acceptReverse(long generation, int status) {
        if (!currentCallbackGeneration(generation, "reverse")) return;
        boolean valid;
        synchronized (VEHICLE_LOCK) {
            reverseAt = SystemClock.elapsedRealtime();
            reverseStatus = status;
            valid = status == 0 || status == 1;
            if (valid) {
                reverseKnown = true;
                reverseLastKnownActive = status == 1;
            } else {
                reverseKnown = false;
            }
        }
        if (!valid) {
            stopForInvalidVehicleState("reverse-status-out-of-domain:" + status);
            return;
        }
        DiagnosticJournal.record("INFO", "xtservice-reverse",
                "known=true status=" + status);
        requestVehicleReevaluation();
    }

    private static void acceptSleep(long generation, int status) {
        if (!currentCallbackGeneration(generation, "sleep")) return;
        boolean valid;
        synchronized (VEHICLE_LOCK) {
            sleepAt = SystemClock.elapsedRealtime();
            sleepStatus = status;
            valid = status == 0 || status == 1;
            if (valid) {
                sleepKnown = true;
                sleepLastKnownActive = status == 1;
            } else {
                sleepKnown = false;
            }
        }
        if (!valid) {
            stopForInvalidVehicleState("sleep-status-out-of-domain:" + status);
            return;
        }
        DiagnosticJournal.record("INFO", "xtservice-sleep",
                "known=true status=" + status);
        requestVehicleReevaluation();
    }

    private static void stopForInvalidVehicleState(String reason) {
        synchronized (VEHICLE_LOCK) {
            vehicleStateUnsafe = true;
            vehicleStateUnsafeReason = reason;
        }
        DiagnosticJournal.state("xtservice-vehicle-state", "STOP", reason);
        requestVehicleReevaluation();
        stopForProcess();
    }

    static VehicleStatePolicy.Decision vehicleDecision() {
        synchronized (VEHICLE_LOCK) {
            if (vehicleStateUnsafe) {
                return VehicleStatePolicy.Decision.veto(vehicleStateUnsafeReason);
            }
            return VehicleStatePolicy.evaluate(reverseKnown, reverseStatus,
                    reverseLastKnownActive, sleepKnown, sleepStatus, sleepLastKnownActive);
        }
    }

    static void qualifyMedia(String action, QualificationCallback callback) {
        boolean unsafe;
        String unsafeReason;
        synchronized (VEHICLE_LOCK) {
            unsafe = vehicleStateUnsafe;
            unsafeReason = vehicleStateUnsafeReason;
        }
        if (stopped || unsafe) {
            finishQualification(callback, false,
                    "XTService observer stopped for this process: " + unsafeReason);
            return;
        }
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
        boolean unsafe;
        String unsafeReason;
        synchronized (VEHICLE_LOCK) {
            unsafe = vehicleStateUnsafe;
            unsafeReason = vehicleStateUnsafeReason;
        }
        if (stopped || unsafe) {
            finishQualification(callback, false,
                    "XTService observer stopped for this process: " + unsafeReason);
            return;
        }
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
        final boolean currentReverseKnown;
        final int currentReverseStatus;
        final long currentReverseAt;
        final boolean currentSleepKnown;
        final int currentSleepStatus;
        final long currentSleepAt;
        final boolean unsafe;
        final String unsafeReason;
        final VehicleStatePolicy.Decision decision;
        synchronized (VEHICLE_LOCK) {
            currentReverseKnown = reverseKnown;
            currentReverseStatus = reverseStatus;
            currentReverseAt = reverseAt;
            currentSleepKnown = sleepKnown;
            currentSleepStatus = sleepStatus;
            currentSleepAt = sleepAt;
            unsafe = vehicleStateUnsafe;
            unsafeReason = vehicleStateUnsafeReason;
            decision = unsafe
                    ? VehicleStatePolicy.Decision.veto(unsafeReason)
                    : VehicleStatePolicy.evaluate(reverseKnown, reverseStatus,
                            reverseLastKnownActive, sleepKnown, sleepStatus,
                            sleepLastKnownActive);
        }
        long now = SystemClock.elapsedRealtime();
        out.putString("xtservice_expected_sha256", ExactXtServiceContract.EXPECTED_APK_SHA256);
        out.putString("xtservice_actual_sha256", actualSha256);
        out.putString("xtservice_version", versionName);
        out.putString("xtservice_identity_state", identityState);
        out.putString("xtservice_identity_detail", identityDetail);
        out.putString("xtservice_bind_state", bindState);
        out.putLong("xtservice_last_bind_attempt_at", lastBindAttemptAt);
        out.putLong("xtservice_active_callback_generation", activeCallbackGeneration);
        out.putBoolean("xtservice_stopped", stopped);
        out.putBoolean("xtservice_bound", bound);
        out.putBoolean("xtservice_callback_registered", callbackRegistered);
        out.putBoolean("xtservice_reverse_known", currentReverseKnown);
        out.putInt("xtservice_reverse_status", currentReverseStatus);
        out.putLong("xtservice_reverse_at", currentReverseAt);
        out.putLong("xtservice_reverse_age_ms",
                currentReverseAt <= 0L ? -1L : now - currentReverseAt);
        out.putBoolean("xtservice_sleep_known", currentSleepKnown);
        out.putInt("xtservice_sleep_status", currentSleepStatus);
        out.putLong("xtservice_sleep_at", currentSleepAt);
        out.putLong("xtservice_sleep_age_ms",
                currentSleepAt <= 0L ? -1L : now - currentSleepAt);
        out.putBoolean("xtservice_vehicle_state_unsafe", unsafe);
        out.putString("xtservice_vehicle_state_unsafe_reason", unsafeReason);
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
        if (current == null) return;
        if (Looper.myLooper() == current.getLooper()) {
            cleanupOnWorker();
        } else {
            current.post(ExactXtServiceObserver::cleanupOnWorker);
        }
    }

    private static void cleanupOnWorker() {
        Handler currentWorker = worker;
        if (currentWorker != null) currentWorker.removeCallbacksAndMessages(null);
        activeCallbackGeneration = 0L;
        unregisterCallbackBestEffort();
        ServiceConnection currentConnection = connection;
        connection = null;
        safeUnbind(currentConnection, "process-stop");
        remote = null;
        bound = false;
        binding = false;
        callbackRegistered = false;
        callbackBinder = null;
        synchronized (VEHICLE_LOCK) {
            reverseKnown = false;
            sleepKnown = false;
            // A terminal observer stop normally relinquishes this optional veto;
            // invalid callback semantics remain vetoed by vehicleStateUnsafe.
            reverseLastKnownActive = false;
            sleepLastKnownActive = false;
        }
        requestVehicleReevaluation();
        HandlerThread currentThread = thread;
        thread = null;
        worker = null;
        if (currentThread != null) currentThread.quitSafely();
    }

    private static void unregisterCallbackBestEffort() {
        IBinder currentRemote = remote;
        ExactXtServiceBinder.CallbackBinder currentCallback = callbackBinder;
        if (currentRemote == null || currentCallback == null || !callbackRegistered) return;
        try {
            ExactXtServiceBinder.unregisterCallback(currentRemote, currentCallback);
        } catch (Throwable t) {
            DiagnosticJournal.record("WARN", "xtservice-unregister",
                    "best-effort callback unregister failed: "
                            + unwrap(t).getClass().getSimpleName());
        }
    }

    private static void safeUnbind(ServiceConnection target, String reason) {
        Context currentContext = context;
        if (target == null || currentContext == null) return;
        try {
            currentContext.unbindService(target);
        } catch (IllegalArgumentException ignored) {
            DiagnosticJournal.record("DEBUG", "xtservice-unbind",
                    "connection already unbound/not registered: " + reason);
        } catch (Throwable t) {
            DiagnosticJournal.record("WARN", "xtservice-unbind",
                    "unbind failed " + reason + ": " + t.getClass().getSimpleName());
        }
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
