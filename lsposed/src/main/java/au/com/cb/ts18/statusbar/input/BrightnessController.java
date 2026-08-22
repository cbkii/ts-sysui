package au.com.cb.ts18.statusbar.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.Calendar;

import de.robv.android.xposed.XposedBridge;

/** Exact-device policy engine; Topway callbacks are semantic authority. */
final class BrightnessController {
    private static final long STATE_RETRY_MS = 1200L;
    private static final BrightnessState STATE = new BrightnessState();
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context context;
    private static HandlerThread workerThread;
    private static Handler worker;
    private static Method getInstance;
    private static Method write2;
    private static Method write3;
    private static boolean attached;
    private static boolean halted;
    private static volatile boolean transportReady;
    private static boolean receiverRegistered;
    private static boolean observerRegistered;
    private static BroadcastReceiver timeReceiver;
    private static ContentObserver settingsObserver;
    private static long lastQueryAt;
    private static PendingAction pending;

    private static volatile long transportReadyAt;
    private static volatile long last258CallbackAt;
    private static volatile long last516CallbackAt;
    private static volatile long lastObservedStockWriteAt;
    private static volatile long lastModuleWriteAt;
    private static volatile String lastObservedStockWrite = "none";
    private static volatile String lastModuleAction = "none";
    private static volatile String confirmationResult = "not-started";
    private static volatile boolean moduleInvocationInFlight;

    private static final Runnable RECONCILE = BrightnessController::reconcileOnWorker;

    private BrightnessController() {}

    static void attach(Context appContext, ClassLoader classLoader) {
        if (appContext == null) return;
        synchronized (LOCK) {
            if (attached || halted) return;
            attached = true;
            context = appContext.getApplicationContext();
            workerThread = new HandlerThread("TS18-Brightness");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());
        }
        Handler current = worker;
        if (current != null) current.post(() -> initialiseOnWorker(classLoader));
    }

    /** Called only after the stock TWSystemUI.init() has completed successfully. */
    static void onTransportReady() {
        transportReady = true;
        transportReadyAt = android.os.SystemClock.elapsedRealtime();
        Handler current = worker;
        if (current != null && BrightnessFeatureRuntime.isOperational() && !halted) {
            current.post(() -> {
                confirmationResult = "transport-ready; querying 258/516 state";
                queryStateOnWorker();
                scheduleReconcile(STATE_RETRY_MS);
            });
        }
    }

    static void onTopwayCallback(int command, int arg1, int arg2) {
        if (!STATE.acceptCallback(command, arg1, arg2)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (command == BrightnessProtocol.COMMAND_MODE) last258CallbackAt = now;
        if (command == BrightnessProtocol.COMMAND_BRIGHTNESS) last516CallbackAt = now;
        transportReady = true;
        if (transportReadyAt == 0L) transportReadyAt = now;
        Handler current = worker;
        if (current != null && BrightnessFeatureRuntime.isOperational() && !halted) {
            current.post(() -> {
                confirmPendingFromCurrentState();
                scheduleReconcile(80L);
            });
        }
    }

    static void onObservedWrite(int command, int arg1, int arg2) {
        if (command != BrightnessProtocol.COMMAND_MODE
                && command != BrightnessProtocol.COMMAND_BRIGHTNESS) return;
        long now = android.os.SystemClock.elapsedRealtime();
        String value = command + ":" + arg1 + ":" + arg2;
        if (moduleInvocationInFlight) {
            lastModuleWriteAt = now;
        } else {
            lastObservedStockWriteAt = now;
            lastObservedStockWrite = value;
        }
        Handler current = worker;
        Context currentContext = context;
        if (current == null || currentContext == null || halted) return;
        current.post(() -> {
            BrightnessPolicy.Config cfg = BrightnessConfig.read(currentContext);
            if (cfg.debug) XposedBridge.log("TS18Brightness: observed TW write command="
                    + command + " arg1=" + arg1 + " arg2=" + arg2
                    + " moduleInvocation=" + moduleInvocationInFlight);
        });
    }

    static void onConfigurationChanged() {
        Handler current = worker;
        if (current == null || halted) return;
        current.post(() -> {
            pending = null;
            confirmationResult = "policy-saved; reconciliation requested";
            if (transportReady && BrightnessFeatureRuntime.isOperational()) {
                queryStateOnWorker();
                scheduleReconcile(STATE_RETRY_MS);
            }
        });
    }

    static void appendStatus(Bundle out) {
        if (out == null) return;
        BrightnessPolicy.State state = STATE.snapshot();
        out.putBoolean("brightness_controller_attached", attached);
        out.putBoolean("brightness_compatible", BrightnessFeatureRuntime.isCompatible());
        out.putBoolean("brightness_transport_ready", transportReady);
        out.putLong("brightness_transport_ready_at", transportReadyAt);
        out.putBoolean("brightness_mode_known", state.modeKnown);
        out.putBoolean("brightness_levels_known", state.levelsKnown);
        out.putInt("brightness_topway_mode", state.topwayMode);
        out.putBoolean("brightness_effective_night", state.effectiveNight);
        out.putInt("brightness_detected_day_level", state.levelsKnown ? state.dayLevel : -1);
        out.putInt("brightness_detected_night_level", state.levelsKnown ? state.nightLevel : -1);
        out.putBoolean("brightness_detected_levels_equal",
                state.levelsKnown && state.dayLevel == state.nightLevel);
        out.putLong("brightness_last_258_callback_at", last258CallbackAt);
        out.putLong("brightness_last_516_callback_at", last516CallbackAt);
        out.putLong("brightness_last_stock_write_at", lastObservedStockWriteAt);
        out.putString("brightness_last_stock_write", lastObservedStockWrite);
        out.putLong("brightness_last_module_write_at", lastModuleWriteAt);
        out.putString("brightness_last_module_action", lastModuleAction);
        out.putString("brightness_confirmation", confirmationResult);
        PendingAction currentPending = pending;
        out.putString("brightness_pending_action",
                currentPending == null ? "none" : currentPending.action.key());
        out.putInt("brightness_pending_attempts",
                currentPending == null ? 0 : currentPending.writeAttempts);
        HandlerThread thread = workerThread;
        out.putBoolean("brightness_worker_alive", thread != null && thread.isAlive());
        out.putBoolean("brightness_watchers_registered", receiverRegistered || observerRegistered);
        out.putString("brightness_runtime_state", runtimeState(state));
    }

    static void stopMutationForProcess() {
        halted = true;
        pending = null;
        confirmationResult = "ERROR: brightness circuit breaker open";
        Handler current = worker;
        if (current == null) {
            cleanupOwnedRuntime();
            return;
        }
        current.removeCallbacksAndMessages(null);
        if (Looper.myLooper() == current.getLooper()) {
            cleanupOwnedRuntime();
        } else {
            boolean posted;
            try {
                posted = current.post(BrightnessController::cleanupOwnedRuntime);
            } catch (Throwable t) {
                posted = false;
                XposedBridge.log("TS18Brightness: breaker cleanup post failed: " + t);
            }
            if (!posted) cleanupOwnedRuntime();
        }
    }

    /** Best-effort ownership-bounded cleanup. Never escalates a breaker cleanup failure. */
    private static void cleanupOwnedRuntime() {
        Context currentContext = context;
        if (currentContext != null && receiverRegistered && timeReceiver != null) {
            try {
                currentContext.unregisterReceiver(timeReceiver);
            } catch (Throwable t) {
                XposedBridge.log("TS18Brightness: time receiver cleanup failed: " + t);
            }
        }
        receiverRegistered = false;
        timeReceiver = null;

        if (currentContext != null && observerRegistered && settingsObserver != null) {
            try {
                currentContext.getContentResolver().unregisterContentObserver(settingsObserver);
            } catch (Throwable t) {
                XposedBridge.log("TS18Brightness: settings observer cleanup failed: " + t);
            }
        }
        observerRegistered = false;
        settingsObserver = null;

        HandlerThread thread;
        synchronized (LOCK) {
            Handler current = worker;
            if (current != null) current.removeCallbacksAndMessages(null);
            worker = null;
            thread = workerThread;
            workerThread = null;
        }
        if (thread != null) {
            try {
                thread.quitSafely();
            } catch (Throwable t) {
                XposedBridge.log("TS18Brightness: worker cleanup failed: " + t);
            }
        }
        XposedBridge.log("TS18Brightness: breaker cleanup removed owned watchers and worker; restart SystemUI to re-arm");
    }

    private static void initialiseOnWorker(ClassLoader classLoader) {
        try {
            BrightnessCompatibility.Result result = BrightnessCompatibility.verify(context);
            if (!result.compatible) {
                BrightnessFeatureRuntime.markIncompatible(result.detail);
                confirmationResult = "BLOCKED: " + result.detail;
                return;
            }
            Class<?> twClass = Class.forName("com.android.systemui.tw.TWSystemUI", false, classLoader);
            getInstance = twClass.getDeclaredMethod("getInstanll");
            write2 = twClass.getDeclaredMethod("write", int.class, int.class);
            write3 = twClass.getDeclaredMethod("write", int.class, int.class, int.class);
            getInstance.setAccessible(true);
            write2.setAccessible(true);
            write3.setAccessible(true);
            registerWatchersOnWorker();
            BrightnessFeatureRuntime.markCompatible();
            confirmationResult = "identity/classes verified; waiting for Topway transport";
            XposedBridge.log("TS18Brightness: exact SystemUI compatibility verified sha256="
                    + result.detail + "; waiting for stock TWSystemUI transport readiness; controller remains inert unless ts18_brightness_enabled=1");
            if (transportReady) {
                queryStateOnWorker();
                scheduleReconcile(STATE_RETRY_MS);
            }
        } catch (Throwable t) {
            confirmationResult = "ERROR: initialise " + t.getClass().getSimpleName();
            BrightnessFeatureRuntime.recordFailure("initialise", t);
        }
    }

    private static void registerWatchersOnWorker() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            timeReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    if (transportReady) scheduleReconcile(0L);
                }
            };
            context.registerReceiver(timeReceiver, filter, null, worker);
            receiverRegistered = true;
        }
        if (!observerRegistered) {
            settingsObserver = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) {
                    if (transportReady) scheduleReconcile(80L);
                }
            };
            for (String key : BrightnessConfig.OBSERVED_KEYS) {
                context.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(key), false, settingsObserver);
            }
            observerRegistered = true;
        }
    }

    private static void reconcileOnWorker() {
        if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()
                || context == null) return;
        BrightnessPolicy.Config config = BrightnessConfig.read(context);
        if (!config.enabled) {
            pending = null;
            confirmationResult = "READY/OFF: policy disabled; stock owns brightness";
            return;
        }
        if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
            confirmationResult = "BLOCKED: scheduled Day/Night times are equal";
            return;
        }

        BrightnessPolicy.State state = STATE.snapshot();
        if (!state.modeKnown || !state.levelsKnown) {
            confirmationResult = !state.modeKnown
                    ? "BLOCKED: waiting for Topway 258 mode callback"
                    : "BLOCKED: waiting for Topway 516 levels callback";
            queryStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        if (pending != null) {
            if (BrightnessActionTracker.matches(pending.action, state)) {
                confirmationResult = "CALLBACK_CONFIRMED: " + pending.action.key();
                pending = null;
            } else {
                handlePendingDeadlineOnWorker();
                if (pending != null) return;
                state = STATE.snapshot();
            }
        }

        int localMinute = currentLocalMinute();
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(config, state, localMinute);
        if (action.type == BrightnessPolicy.ActionType.NONE) {
            confirmationResult = "ACTIVE/SETTLED: Topway policy matches requested state";
            scheduleNextTransitionOnWorker(config, localMinute);
            if (config.debug) logState("settled", config, state, localMinute);
            return;
        }

        startPendingActionOnWorker(action);
    }

    private static void confirmPendingFromCurrentState() {
        PendingAction current = pending;
        if (current == null) return;
        BrightnessPolicy.State state = STATE.snapshot();
        if (BrightnessActionTracker.matches(current.action, state)) {
            confirmationResult = "CALLBACK_CONFIRMED: " + current.action.key();
            pending = null;
        }
    }

    private static void startPendingActionOnWorker(BrightnessPolicy.Action action) {
        long now = android.os.SystemClock.elapsedRealtime();
        pending = new PendingAction(action, 1, false,
                now, now + BrightnessActionTracker.WRITE_CONFIRM_MS);
        confirmationResult = "ACTION_PENDING: " + action.key();
        lastModuleAction = action.key();
        postActionToMain(action);
        scheduleReconcile(BrightnessActionTracker.WRITE_CONFIRM_MS + 50L);
    }

    private static void handlePendingDeadlineOnWorker() {
        PendingAction current = pending;
        if (current == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        BrightnessActionTracker.DeadlineDecision decision =
                BrightnessActionTracker.onDeadline(now, current.deadlineMs,
                        current.writeAttempts, current.queriedAfterWrite);
        switch (decision) {
            case WAIT:
                scheduleReconcile(Math.max(80L, current.deadlineMs - now));
                return;
            case QUERY:
                current.queriedAfterWrite = true;
                current.deadlineMs = now + BrightnessActionTracker.QUERY_CONFIRM_MS;
                confirmationResult = "ACTION_PENDING: unconfirmed; querying 258/516 before retry";
                queryStateOnWorker();
                scheduleReconcile(BrightnessActionTracker.QUERY_CONFIRM_MS + 50L);
                return;
            case RETRY_WRITE:
                current.writeAttempts++;
                current.queriedAfterWrite = false;
                current.sentAtMs = now;
                current.deadlineMs = now + BrightnessActionTracker.RETRY_CONFIRM_MS;
                confirmationResult = "ACTION_PENDING: bounded retry " + current.writeAttempts
                        + '/' + BrightnessActionTracker.MAX_WRITE_ATTEMPTS + " "
                        + current.action.key();
                lastModuleAction = current.action.key();
                postActionToMain(current.action);
                scheduleReconcile(BrightnessActionTracker.RETRY_CONFIRM_MS + 50L);
                return;
            case FAIL:
            default:
                String reason = BrightnessActionTracker.missingCallbackReason(current.action);
                String actionKey = current.action.key();
                pending = null;
                confirmationResult = "ERROR: " + reason + " for " + actionKey;
                BrightnessFeatureRuntime.recordFailure("unconfirmed-" + reason,
                        new IllegalStateException("Topway state did not confirm " + actionKey
                                + " after bounded query/retry sequence"));
        }
    }

    private static void postActionToMain(BrightnessPolicy.Action action) {
        MAIN.post(() -> {
            if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()) return;
            try {
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                moduleInvocationInFlight = true;
                switch (action.type) {
                    case SET_DAY_LEVEL:
                    case SET_NIGHT_LEVEL:
                        write3.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS,
                                action.selector, action.value);
                        break;
                    case SET_MODE:
                        write3.invoke(instance, BrightnessProtocol.COMMAND_MODE,
                                BrightnessProtocol.MODE_WRITE_SELECTOR, action.value);
                        break;
                    case NONE:
                    default:
                        break;
                }
            } catch (Throwable t) {
                confirmationResult = "ERROR: Topway write " + unwrap(t).getClass().getSimpleName();
                BrightnessFeatureRuntime.recordFailure("topway-write", unwrap(t));
            } finally {
                moduleInvocationInFlight = false;
            }
        });
    }

    private static void queryStateOnWorker() {
        if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastQueryAt < 350L) return;
        lastQueryAt = now;
        MAIN.post(() -> {
            if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()) return;
            try {
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                moduleInvocationInFlight = true;
                write2.invoke(instance, BrightnessProtocol.COMMAND_MODE,
                        BrightnessProtocol.QUERY_VALUE);
                write2.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS,
                        BrightnessProtocol.QUERY_VALUE);
            } catch (Throwable t) {
                confirmationResult = "ERROR: Topway query " + unwrap(t).getClass().getSimpleName();
                BrightnessFeatureRuntime.recordFailure("topway-query", unwrap(t));
            } finally {
                moduleInvocationInFlight = false;
            }
        });
    }

    private static void scheduleReconcile(long delayMs) {
        Handler current = worker;
        if (current == null || halted || !transportReady) return;
        current.removeCallbacks(RECONCILE);
        current.postDelayed(RECONCILE, Math.max(0L, delayMs));
    }

    private static void scheduleNextTransitionOnWorker(BrightnessPolicy.Config config, int localMinute) {
        Handler current = worker;
        if (current == null || halted || !transportReady) return;
        current.removeCallbacks(RECONCILE);
        if (config.mode != BrightnessPolicy.ControlMode.SET_AUTO || !config.scheduleValid()) return;
        int minutes = BrightnessPolicy.minutesUntilNextTransition(config, localMinute);
        if (minutes < 0) return;
        Calendar now = Calendar.getInstance();
        long intoMinute = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND);
        long delay = minutes * 60_000L - intoMinute;
        if (delay < 1000L) delay = 1000L;
        current.postDelayed(RECONCILE, delay);
    }

    private static int currentLocalMinute() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static void logState(String reason, BrightnessPolicy.Config config,
                                 BrightnessPolicy.State state, int localMinute) {
        XposedBridge.log("TS18Brightness: " + reason + " policy=" + config.mode.persisted
                + " topwayMode=" + state.topwayMode + " day=" + state.dayLevel
                + " night=" + state.nightLevel + " effectiveNight=" + state.effectiveNight
                + " localMinute=" + localMinute);
    }

    private static String runtimeState(BrightnessPolicy.State state) {
        if (BrightnessFeatureRuntime.isBreakerOpen() || halted) return "ERROR:BREAKER_OPEN";
        if (!attached) return "BLOCKED:HOOK_NOT_ATTACHED";
        if (!BrightnessFeatureRuntime.isCompatible()) return "BLOCKED:IDENTITY_OR_CLASS_CONTRACT";
        if (!transportReady) return "BLOCKED:TRANSPORT_NOT_READY";
        if (!state.modeKnown) return "BLOCKED:MODE_STATE_UNKNOWN";
        if (!state.levelsKnown) return "BLOCKED:LEVEL_STATE_UNKNOWN";
        if (pending != null) return "ACTION_PENDING";
        Context currentContext = context;
        if (currentContext == null || !BrightnessConfig.read(currentContext).enabled) return "READY/OFF";
        return "ACTIVE/SETTLED";
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t.getCause();
        return cause == null ? t : cause;
    }

    private static final class PendingAction {
        final BrightnessPolicy.Action action;
        int writeAttempts;
        boolean queriedAfterWrite;
        long sentAtMs;
        long deadlineMs;

        PendingAction(BrightnessPolicy.Action action, int writeAttempts,
                      boolean queriedAfterWrite, long sentAtMs, long deadlineMs) {
            this.action = action;
            this.writeAttempts = writeAttempts;
            this.queriedAfterWrite = queriedAfterWrite;
            this.sentAtMs = sentAtMs;
            this.deadlineMs = deadlineMs;
        }
    }
}
