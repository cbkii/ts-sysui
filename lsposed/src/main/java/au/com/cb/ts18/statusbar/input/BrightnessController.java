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
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XposedBridge;

/** Exact-device policy engine using the proven Topway 258/516 SystemUI contract. */
final class BrightnessController {
    private static final long STATE_RETRY_MS = 1200L;
    private static final BrightnessState STATE = new BrightnessState();
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicLong ACTION_GENERATION = new AtomicLong();

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
    private static volatile long lastAndroidMirrorReadAt;
    private static volatile long lastModeStage1At;
    private static volatile long lastModeStage2At;
    private static volatile String lastObservedStockWrite = "none";
    private static volatile String lastModuleAction = "none";
    private static volatile String lastModeTransaction = "none";
    private static volatile String confirmationResult = "not-started";
    private static volatile boolean moduleInvocationInFlight;
    private static volatile int lastObservedScreenBrightnessRaw = -1;

    private static final Runnable RECONCILE = BrightnessController::reconcileOnWorker;

    private BrightnessController() {}

    static void attach(Context appContext, ClassLoader classLoader) {
        if (appContext == null) return;
        synchronized (LOCK) {
            if (attached || halted) return;
            attached = true;
            context = appContext.getApplicationContext();
            if (context == null) context = appContext;
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
                confirmationResult = "transport-ready; querying proven Topway 258/516 state";
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
        // Invalidate any action already queued to SystemUI's main looper before
        // the worker reads the newly persisted policy. The runnable also
        // re-authorises against current config/state immediately before mutation.
        ACTION_GENERATION.incrementAndGet();
        Handler current = worker;
        if (current == null || halted) return;
        current.post(() -> {
            pending = null;
            refreshAndroidMirrorOnWorker();
            confirmationResult = "policy-saved; Topway reconciliation requested";
            if (transportReady && BrightnessFeatureRuntime.isOperational()) queryStateOnWorker();
            scheduleReconcile(0L);
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
        out.putBoolean("brightness_topway_slots_observation_only", false);
        out.putBoolean("brightness_detected_levels_equal",
                state.levelsKnown && state.dayLevel == state.nightLevel);
        out.putString("brightness_primary_backend", "Topway 516 via existing TWSystemUI");
        out.putString("brightness_android_mirror_role", "diagnostic-only; not mutation authority");
        out.putString("brightness_carsetting_contract_sha256",
                BrightnessCompatibility.EXPECTED_CARSETTING_SHA256);
        out.putLong("brightness_action_generation", ACTION_GENERATION.get());
        out.putInt("brightness_screen_raw", lastObservedScreenBrightnessRaw);
        out.putLong("brightness_last_android_mirror_read_at", lastAndroidMirrorReadAt);
        out.putLong("brightness_last_mode_stage1_at", lastModeStage1At);
        out.putLong("brightness_last_mode_stage2_at", lastModeStage2At);
        out.putString("brightness_mode_transaction", lastModeTransaction);
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
        ACTION_GENERATION.incrementAndGet();
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
            refreshAndroidMirrorOnWorker();
            confirmationResult = "exact contract verified; waiting for Topway 258/516 transport/state";
            XposedBridge.log("TS18Brightness: exact brightness contract verified "
                    + result.detail + "; primary backend=Topway 516 via existing TWSystemUI; "
                    + "Settings.System.SCREEN_BRIGHTNESS retained as diagnostic mirror only; "
                    + "controller remains inert unless ts18_brightness_enabled=1");
            if (transportReady) queryStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
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
                    Handler current = worker;
                    if (current != null) current.post(() -> {
                        refreshAndroidMirrorOnWorker();
                        queryStateOnWorker();
                        scheduleReconcile(0L);
                    });
                }
            };
            context.registerReceiver(timeReceiver, filter, null, worker);
            receiverRegistered = true;
        }
        if (!observerRegistered) {
            settingsObserver = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) {
                    refreshAndroidMirrorOnWorker();
                }
            };
            for (String key : BrightnessConfig.OBSERVED_KEYS) {
                context.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(key), false, settingsObserver);
            }
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, settingsObserver);
            observerRegistered = true;
        }
    }

    private static void reconcileOnWorker() {
        if (halted || !BrightnessFeatureRuntime.isOperational() || context == null) return;
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
        if (!transportReady) {
            confirmationResult = "BLOCKED: Topway transport not ready";
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        BrightnessPolicy.State state = STATE.snapshot();
        if (!state.modeKnown) {
            confirmationResult = "BLOCKED: waiting for Topway 258 mode callback";
            queryStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }
        if (config.hasManagedLevel() && !state.levelsKnown) {
            confirmationResult = "BLOCKED: waiting for Topway 516 Day/Night callback";
            queryStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        if (pending != null) {
            if (BrightnessActionTracker.matches(pending.action, state)) {
                confirmationResult = confirmedMessage(pending.action);
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
            confirmationResult = "ACTIVE/SETTLED: Topway 258/516 policy matches requested state; "
                    + "Android screen_brightness mirror=" + lastObservedScreenBrightnessRaw;
            scheduleNextTransitionOnWorker(config, localMinute);
            if (config.debug) logState("settled", config, state, localMinute);
            return;
        }
        startPendingActionOnWorker(action);
    }

    private static String confirmedMessage(BrightnessPolicy.Action action) {
        if (action.type == BrightnessPolicy.ActionType.SET_MODE) {
            return "CALLBACK_CONFIRMED: " + action.key() + " modeTransaction=" + lastModeTransaction;
        }
        return "CALLBACK_CONFIRMED: " + action.key() + " via Topway 516";
    }

    private static void confirmPendingFromCurrentState() {
        PendingAction current = pending;
        if (current == null) return;
        if (current.generation != ACTION_GENERATION.get()) {
            pending = null;
            confirmationResult = "ACTION_CANCELLED: policy generation changed before confirmation";
            scheduleReconcile(0L);
            return;
        }
        BrightnessPolicy.State state = STATE.snapshot();
        if (BrightnessActionTracker.matches(current.action, state)) {
            confirmationResult = confirmedMessage(current.action);
            pending = null;
        }
    }

    private static void startPendingActionOnWorker(BrightnessPolicy.Action action) {
        long now = android.os.SystemClock.elapsedRealtime();
        long generation = ACTION_GENERATION.get();
        pending = new PendingAction(action, 1, false,
                now, now + BrightnessActionTracker.WRITE_CONFIRM_MS, generation);
        confirmationResult = "ACTION_PENDING: " + action.key();
        lastModuleAction = action.key();
        postActionToMain(action, generation);
        scheduleReconcile(BrightnessActionTracker.WRITE_CONFIRM_MS + 50L);
    }

    private static void handlePendingDeadlineOnWorker() {
        PendingAction current = pending;
        if (current == null) return;
        if (current.generation != ACTION_GENERATION.get()) {
            pending = null;
            confirmationResult = "ACTION_CANCELLED: policy generation changed before retry";
            scheduleReconcile(0L);
            return;
        }
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
                confirmationResult = "ACTION_PENDING: unconfirmed; querying Topway 258/516 before retry";
                queryStateOnWorker();
                scheduleReconcile(BrightnessActionTracker.QUERY_CONFIRM_MS + 50L);
                return;
            case RETRY_WRITE:
                current.writeAttempts++;
                current.queriedAfterWrite = false;
                current.sentAtMs = now;
                current.deadlineMs = now + BrightnessActionTracker.RETRY_CONFIRM_MS;
                confirmationResult = "ACTION_PENDING: bounded retry " + current.writeAttempts
                        + '/' + BrightnessActionTracker.MAX_WRITE_ATTEMPTS + " " + current.action.key();
                lastModuleAction = current.action.key();
                postActionToMain(current.action, current.generation);
                scheduleReconcile(BrightnessActionTracker.RETRY_CONFIRM_MS + 50L);
                return;
            case FAIL:
            default:
                String reason = BrightnessActionTracker.missingConfirmationReason(current.action);
                String actionKey = current.action.key();
                pending = null;
                confirmationResult = "ERROR: " + reason + " for " + actionKey
                        + " modeTransaction=" + lastModeTransaction;
                BrightnessFeatureRuntime.recordFailure("unconfirmed-" + reason,
                        new IllegalStateException("Topway state did not confirm " + actionKey
                                + " after bounded query/retry sequence"));
        }
    }

    private static void postActionToMain(BrightnessPolicy.Action action, long generation) {
        MAIN.post(() -> {
            if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()
                    || generation != ACTION_GENERATION.get()) {
                cancelPendingAction(action, generation,
                        "generation/runtime changed before main-looper dispatch");
                return;
            }
            Context currentContext = context;
            if (currentContext == null) {
                cancelPendingAction(action, generation, "SystemUI context unavailable");
                return;
            }
            BrightnessPolicy.Config currentConfig = BrightnessConfig.read(currentContext);
            BrightnessPolicy.State currentState = STATE.snapshot();
            BrightnessPolicy.Action nowRequired = BrightnessPolicy.nextAction(
                    currentConfig, currentState, currentLocalMinute());
            if (!sameAction(action, nowRequired)) {
                cancelPendingAction(action, generation,
                        "policy/state changed before main-looper mutation");
                return;
            }

            try {
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                moduleInvocationInFlight = true;
                switch (action.type) {
                    case SET_DAY_LEVEL:
                    case SET_NIGHT_LEVEL:
                        write3.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS,
                                action.selector, action.value);
                        lastModuleWriteAt = android.os.SystemClock.elapsedRealtime();
                        DiagnosticJournal.record("INFO", "brightness-516-write",
                                "selector=" + action.selector + " level=" + action.value);
                        break;
                    case SET_MODE:
                        lastModeTransaction = "stage1-pending";
                        write3.invoke(instance, BrightnessProtocol.COMMAND_MODE,
                                BrightnessProtocol.MODE_WRITE_SELECTOR, action.value);
                        lastModeStage1At = android.os.SystemClock.elapsedRealtime();
                        lastModuleWriteAt = lastModeStage1At;
                        lastModeTransaction = "stage1-sent;stage2-pending";
                        write2.invoke(instance, BrightnessProtocol.COMMAND_MODE,
                                BrightnessProtocol.MODE_TRANSACTION_SECOND_VALUE);
                        lastModeStage2At = android.os.SystemClock.elapsedRealtime();
                        lastModuleWriteAt = lastModeStage2At;
                        lastModeTransaction = "stage1-sent;stage2-sent";
                        DiagnosticJournal.record("INFO", "brightness-mode-transaction",
                                "258,1," + action.value + " -> 258,"
                                        + BrightnessProtocol.MODE_TRANSACTION_SECOND_VALUE);
                        break;
                    case NONE:
                    default:
                        break;
                }
            } catch (Throwable t) {
                Throwable error = unwrap(t);
                confirmationResult = "ERROR: Topway write " + error.getClass().getSimpleName()
                        + " action=" + action.key() + " modeTransaction=" + lastModeTransaction;
                BrightnessFeatureRuntime.recordFailure("topway-write", error);
            } finally {
                moduleInvocationInFlight = false;
            }
        });
    }

    private static void cancelPendingAction(BrightnessPolicy.Action action, long generation,
                                            String reason) {
        Handler current = worker;
        if (current == null) return;
        current.post(() -> {
            PendingAction active = pending;
            if (active == null || active.generation != generation
                    || !sameAction(active.action, action)) return;
            pending = null;
            lastModuleAction = "cancelled:" + action.key();
            confirmationResult = "ACTION_CANCELLED: " + reason + " action=" + action.key();
            DiagnosticJournal.record("INFO", "brightness-action-cancelled",
                    "generation=" + generation + " reason=" + reason + " action=" + action.key());
            scheduleReconcile(0L);
        });
    }

    private static boolean sameAction(BrightnessPolicy.Action left, BrightnessPolicy.Action right) {
        return left != null && right != null
                && left.type == right.type && left.selector == right.selector && left.value == right.value;
    }

    private static void queryStateOnWorker() {
        refreshAndroidMirrorOnWorker();
        if (!transportReady || !BrightnessFeatureRuntime.isOperational() || halted) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastQueryAt < 350L) return;
        lastQueryAt = now;
        MAIN.post(() -> {
            if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()) return;
            try {
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                moduleInvocationInFlight = true;
                write2.invoke(instance, BrightnessProtocol.COMMAND_MODE, BrightnessProtocol.QUERY_VALUE);
                write2.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS, BrightnessProtocol.QUERY_VALUE);
            } catch (Throwable t) {
                confirmationResult = "ERROR: Topway query " + unwrap(t).getClass().getSimpleName();
                BrightnessFeatureRuntime.recordFailure("topway-query", unwrap(t));
            } finally {
                moduleInvocationInFlight = false;
            }
        });
    }

    private static void refreshAndroidMirrorOnWorker() {
        Context currentContext = context;
        if (currentContext == null || halted) return;
        try {
            lastObservedScreenBrightnessRaw = Settings.System.getInt(
                    currentContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
            lastAndroidMirrorReadAt = android.os.SystemClock.elapsedRealtime();
            DiagnosticJournal.record("DEBUG", "brightness-android-mirror-read",
                    "screen_brightness=" + lastObservedScreenBrightnessRaw);
        } catch (Throwable t) {
            lastObservedScreenBrightnessRaw = -1;
            RateLimitedLog.error("brightness-android-mirror-read",
                    "could not read Settings.System.SCREEN_BRIGHTNESS", t);
        }
    }

    private static void scheduleReconcile(long delayMs) {
        Handler current = worker;
        if (current == null || halted) return;
        current.removeCallbacks(RECONCILE);
        current.postDelayed(RECONCILE, Math.max(0L, delayMs));
    }

    private static void scheduleNextTransitionOnWorker(BrightnessPolicy.Config config, int localMinute) {
        Handler current = worker;
        if (current == null || halted) return;
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
                + " androidMirror=" + lastObservedScreenBrightnessRaw
                + " localMinute=" + localMinute);
    }

    private static String runtimeState(BrightnessPolicy.State state) {
        if (BrightnessFeatureRuntime.isBreakerOpen() || halted) return "ERROR:BREAKER_OPEN";
        if (!attached) return "BLOCKED:HOOK_NOT_ATTACHED";
        if (!BrightnessFeatureRuntime.isCompatible()) return "BLOCKED:IDENTITY_OR_CLASS_CONTRACT";
        Context currentContext = context;
        if (currentContext == null) return "BLOCKED:CONTEXT_UNAVAILABLE";
        BrightnessPolicy.Config config = BrightnessConfig.read(currentContext);
        if (!config.enabled) return "READY/OFF";
        if (!transportReady) return "BLOCKED:TRANSPORT_NOT_READY";
        if (!state.modeKnown) return "BLOCKED:MODE_STATE_UNKNOWN";
        if (config.hasManagedLevel() && !state.levelsKnown) return "BLOCKED:LEVEL_STATE_UNKNOWN";
        if (pending != null) return "ACTION_PENDING";
        return "ACTIVE/SETTLED";
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t.getCause();
        return cause == null ? t : cause;
    }

    private static final class PendingAction {
        final BrightnessPolicy.Action action;
        final long generation;
        int writeAttempts;
        boolean queriedAfterWrite;
        long sentAtMs;
        long deadlineMs;

        PendingAction(BrightnessPolicy.Action action, int writeAttempts,
                      boolean queriedAfterWrite, long sentAtMs, long deadlineMs,
                      long generation) {
            this.action = action;
            this.writeAttempts = writeAttempts;
            this.queriedAfterWrite = queriedAfterWrite;
            this.sentAtMs = sentAtMs;
            this.deadlineMs = deadlineMs;
            this.generation = generation;
        }
    }
}
