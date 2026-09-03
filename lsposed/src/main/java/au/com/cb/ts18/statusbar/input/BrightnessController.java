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

/** Exact-device policy engine; Topway state is semantic authority and CarSetting defines physical output. */
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
    private static volatile long lastPhysicalWriteAt;
    private static volatile long lastPhysicalReadAt;
    private static volatile long lastModeStage1At;
    private static volatile long lastModeStage2At;
    private static volatile String lastObservedStockWrite = "none";
    private static volatile String lastModuleAction = "none";
    private static volatile String lastModeTransaction = "none";
    private static volatile String confirmationResult = "not-started";
    private static volatile boolean moduleInvocationInFlight;
    private static volatile int lastObservedScreenBrightnessRaw = -1;
    private static volatile int lastRequestedLogicalLevel = BrightnessPolicy.PRESERVE_LEVEL;
    private static volatile int lastRequestedScreenBrightnessRaw = -1;

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
                confirmationResult = "transport-ready; querying Topway 258/516 observation state";
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
        // Invalidate any action already queued to the SystemUI main looper before
        // the worker observes the new policy. The main-looper runnable also
        // re-authorises against current config/state immediately before mutation.
        ACTION_GENERATION.incrementAndGet();
        Handler current = worker;
        if (current == null || halted) return;
        current.post(() -> {
            pending = null;
            refreshPhysicalBrightnessOnWorker();
            confirmationResult = "policy-saved; reconciliation requested; screen_brightness="
                    + lastObservedScreenBrightnessRaw;
            if (transportReady && BrightnessFeatureRuntime.isOperational()) {
                queryTopwayStateOnWorker();
            }
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
        out.putBoolean("brightness_topway_slots_observation_only", true);
        out.putBoolean("brightness_detected_levels_equal",
                state.levelsKnown && state.dayLevel == state.nightLevel);
        out.putString("brightness_physical_backend", "Settings.System.SCREEN_BRIGHTNESS");
        out.putString("brightness_carsetting_contract_sha256",
                BrightnessCompatibility.EXPECTED_CARSETTING_SHA256);
        out.putLong("brightness_action_generation", ACTION_GENERATION.get());
        out.putInt("brightness_screen_raw", lastObservedScreenBrightnessRaw);
        out.putInt("brightness_requested_logical_level", lastRequestedLogicalLevel);
        out.putInt("brightness_requested_screen_raw", lastRequestedScreenBrightnessRaw);
        out.putLong("brightness_last_physical_write_at", lastPhysicalWriteAt);
        out.putLong("brightness_last_physical_read_at", lastPhysicalReadAt);
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
            refreshPhysicalBrightnessOnWorker();
            confirmationResult = "exact SystemUI/CarSetting contract verified; screen_brightness="
                    + lastObservedScreenBrightnessRaw + "; waiting for Topway transport";
            XposedBridge.log("TS18Brightness: exact brightness contract verified "
                    + result.detail + "; physical backend=Settings.System.SCREEN_BRIGHTNESS; "
                    + "Topway 516 retained as observation-only; controller remains inert unless ts18_brightness_enabled=1");
            if (transportReady) queryTopwayStateOnWorker();
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
                        refreshPhysicalBrightnessOnWorker();
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
                    refreshPhysicalBrightnessOnWorker();
                    scheduleReconcile(80L);
                }
            };
            for (String key : BrightnessConfig.OBSERVED_KEYS) {
                context.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(key), false, settingsObserver);
            }
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false, settingsObserver);
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

        if (lastObservedScreenBrightnessRaw < 0) refreshPhysicalBrightnessOnWorker();
        if (!transportReady) {
            confirmationResult = "BLOCKED: Topway transport not ready; physical screen_brightness observed="
                    + lastObservedScreenBrightnessRaw;
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        BrightnessPolicy.State state = STATE.snapshot();
        if (!state.modeKnown) {
            confirmationResult = "BLOCKED: waiting for Topway 258 mode callback";
            queryTopwayStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        if (pending != null) {
            if (BrightnessActionTracker.matches(pending.action, state,
                    lastObservedScreenBrightnessRaw)) {
                confirmationResult = confirmedMessage(pending.action);
                pending = null;
            } else {
                handlePendingDeadlineOnWorker();
                if (pending != null) return;
                state = STATE.snapshot();
            }
        }

        int localMinute = currentLocalMinute();
        BrightnessPolicy.LevelTarget target =
                BrightnessPolicy.targetPhysicalLevel(config, state, localMinute);
        if (!target.known) {
            confirmationResult = "BLOCKED:AUTO_EFFECTIVE_STATE_UNKNOWN: 516 observation required; "
                    + "physical screen_brightness remains stock/current="
                    + lastObservedScreenBrightnessRaw;
            queryTopwayStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }
        if (target.managed() && lastObservedScreenBrightnessRaw < 0) {
            confirmationResult = "BLOCKED:SCREEN_BRIGHTNESS_UNKNOWN";
            refreshPhysicalBrightnessOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(
                config, state, localMinute, lastObservedScreenBrightnessRaw);
        if (action.type == BrightnessPolicy.ActionType.NONE) {
            confirmationResult = settledMessage(config, state, target);
            scheduleNextTransitionOnWorker(config, localMinute);
            if (config.debug) logState("settled", config, state, localMinute, target);
            return;
        }

        startPendingActionOnWorker(action);
    }

    private static String settledMessage(BrightnessPolicy.Config config,
                                         BrightnessPolicy.State state,
                                         BrightnessPolicy.LevelTarget target) {
        String targetText = target.managed()
                ? (target.selector == BrightnessPolicy.SLOT_NIGHT ? "night" : "day")
                + " logical=" + target.logicalLevel
                + " raw=" + BrightnessLevelMapper.logicalToRaw(target.logicalLevel)
                : target.reason;
        return "ACTIVE/SETTLED: mode=" + state.topwayMode
                + " target=" + targetText
                + " observedRaw=" + lastObservedScreenBrightnessRaw
                + "; Topway516=observation-only";
    }

    private static String confirmedMessage(BrightnessPolicy.Action action) {
        if (action.type == BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL) {
            return "READBACK_CONFIRMED: logical=" + action.value
                    + " raw=" + action.rawBrightness()
                    + " observedRaw=" + lastObservedScreenBrightnessRaw;
        }
        return "CALLBACK_CONFIRMED: " + action.key()
                + " modeTransaction=" + lastModeTransaction;
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
        if (BrightnessActionTracker.matches(current.action, state,
                lastObservedScreenBrightnessRaw)) {
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
        if (action.type == BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL) {
            lastRequestedLogicalLevel = action.value;
            lastRequestedScreenBrightnessRaw = action.rawBrightness();
        }
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
                if (current.action.type == BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL) {
                    confirmationResult = "ACTION_PENDING: physical write unconfirmed; reading screen_brightness before retry";
                    refreshPhysicalBrightnessOnWorker();
                } else {
                    confirmationResult = "ACTION_PENDING: mode unconfirmed; querying Topway 258 before retry";
                    queryTopwayStateOnWorker();
                }
                confirmPendingFromCurrentState();
                if (pending != null) {
                    scheduleReconcile(BrightnessActionTracker.QUERY_CONFIRM_MS + 50L);
                }
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
                postActionToMain(current.action, current.generation);
                scheduleReconcile(BrightnessActionTracker.RETRY_CONFIRM_MS + 50L);
                return;
            case FAIL:
            default:
                String reason = BrightnessActionTracker.missingConfirmationReason(current.action);
                String actionKey = current.action.key();
                pending = null;
                confirmationResult = "ERROR: " + reason + " for " + actionKey
                        + " observedRaw=" + lastObservedScreenBrightnessRaw
                        + " modeTransaction=" + lastModeTransaction;
                BrightnessFeatureRuntime.recordFailure("unconfirmed-" + reason,
                        new IllegalStateException("brightness state did not confirm " + actionKey
                                + " after bounded read/query/retry sequence"));
        }
    }

    private static void postActionToMain(BrightnessPolicy.Action action, long generation) {
        MAIN.post(() -> {
            if (halted || !BrightnessFeatureRuntime.isOperational()
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
            if (!actionStillAuthorised(action, currentConfig, currentState,
                    currentLocalMinute(), lastObservedScreenBrightnessRaw)) {
                cancelPendingAction(action, generation,
                        "policy/state changed before main-looper mutation");
                return;
            }

            try {
                if (action.type == BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL) {
                    int raw = action.rawBrightness();
                    boolean written = Settings.System.putInt(currentContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, raw);
                    if (!written) {
                        throw new IllegalStateException("Settings.System rejected screen_brightness=" + raw);
                    }
                    lastPhysicalWriteAt = android.os.SystemClock.elapsedRealtime();
                    lastModuleWriteAt = lastPhysicalWriteAt;
                    DiagnosticJournal.record("INFO", "brightness-physical-write",
                            "logical=" + action.value + " raw=" + raw
                                    + " backend=Settings.System.SCREEN_BRIGHTNESS");
                    Handler current = worker;
                    if (current != null) current.post(BrightnessController::refreshPhysicalBrightnessOnWorker);
                    return;
                }

                if (action.type != BrightnessPolicy.ActionType.SET_MODE) return;
                if (!transportReady) throw new IllegalStateException("Topway transport is not ready");
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                moduleInvocationInFlight = true;
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
            } catch (Throwable t) {
                Throwable error = unwrap(t);
                confirmationResult = "ERROR: brightness action "
                        + error.getClass().getSimpleName()
                        + " action=" + action.key()
                        + " modeTransaction=" + lastModeTransaction;
                BrightnessFeatureRuntime.recordFailure("action", error);
            } finally {
                moduleInvocationInFlight = false;
            }
        });
    }

    private static boolean actionStillAuthorised(BrightnessPolicy.Action action,
                                                 BrightnessPolicy.Config config,
                                                 BrightnessPolicy.State state,
                                                 int localMinute,
                                                 int observedScreenBrightnessRaw) {
        if (action == null || config == null || state == null || !config.enabled
                || !state.modeKnown) return false;
        int desiredMode = BrightnessPolicy.desiredTopwayMode(config, localMinute);
        if (action.type == BrightnessPolicy.ActionType.SET_MODE) {
            return action.value == desiredMode && state.topwayMode != desiredMode;
        }
        if (action.type != BrightnessPolicy.ActionType.SET_PHYSICAL_LEVEL
                || state.topwayMode != desiredMode) return false;
        BrightnessPolicy.LevelTarget target =
                BrightnessPolicy.targetPhysicalLevel(config, state, localMinute);
        return target.known && target.managed()
                && target.selector == action.selector
                && target.logicalLevel == action.value
                && !BrightnessLevelMapper.matchesLogical(target.logicalLevel,
                        observedScreenBrightnessRaw);
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
                    "generation=" + generation + " reason=" + reason
                            + " action=" + action.key());
            scheduleReconcile(0L);
        });
    }

    private static boolean sameAction(BrightnessPolicy.Action left, BrightnessPolicy.Action right) {
        return left != null && right != null
                && left.type == right.type
                && left.selector == right.selector
                && left.value == right.value;
    }

    private static void queryStateOnWorker() {
        refreshPhysicalBrightnessOnWorker();
        if (transportReady) queryTopwayStateOnWorker();
    }

    private static void queryTopwayStateOnWorker() {
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
                // 516 is observation-only on this exact CarSetting build. Querying
                // it is retained solely for effective Day/Night/slot observation.
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

    private static void refreshPhysicalBrightnessOnWorker() {
        Context currentContext = context;
        if (currentContext == null || halted) return;
        try {
            int raw = Settings.System.getInt(currentContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
            lastObservedScreenBrightnessRaw = raw;
            lastPhysicalReadAt = android.os.SystemClock.elapsedRealtime();
            DiagnosticJournal.record("DEBUG", "brightness-physical-read",
                    "screen_brightness=" + raw);
            confirmPendingFromCurrentState();
        } catch (Throwable t) {
            lastObservedScreenBrightnessRaw = -1;
            RateLimitedLog.error("brightness-physical-read",
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
                                 BrightnessPolicy.State state, int localMinute,
                                 BrightnessPolicy.LevelTarget target) {
        XposedBridge.log("TS18Brightness: " + reason + " policy=" + config.mode.persisted
                + " topwayMode=" + state.topwayMode
                + " callback516Day=" + state.dayLevel
                + " callback516Night=" + state.nightLevel
                + " effectiveNight=" + state.effectiveNight
                + " target=" + target.reason + ':' + target.logicalLevel
                + " screenBrightnessRaw=" + lastObservedScreenBrightnessRaw
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
        if (config.mode == BrightnessPolicy.ControlMode.AUTO
                && config.hasManagedLevel() && !state.levelsKnown) {
            return "BLOCKED:AUTO_EFFECTIVE_STATE_UNKNOWN";
        }
        BrightnessPolicy.LevelTarget target =
                BrightnessPolicy.targetPhysicalLevel(config, state, currentLocalMinute());
        if (target.managed() && lastObservedScreenBrightnessRaw < 0) {
            return "BLOCKED:SCREEN_BRIGHTNESS_UNKNOWN";
        }
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
