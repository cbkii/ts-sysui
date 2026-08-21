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
import android.os.ResultReceiver;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.Calendar;

import de.robv.android.xposed.XposedBridge;

/** Exact-device policy engine; Topway callbacks are semantic authority. */
final class BrightnessController {
    private static final long CONFIRM_DELAY_MS = 450L;
    private static final long STATE_RETRY_MS = 1000L;
    private static final int MAX_SAME_ACTION_ATTEMPTS = 3;
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
    private static boolean configReceiverRegistered;
    private static boolean observerRegistered;
    private static BroadcastReceiver timeReceiver;
    private static BroadcastReceiver configReceiver;
    private static ContentObserver settingsObserver;
    private static String lastActionKey;
    private static int sameActionAttempts;
    private static long lastQueryAt;

    private static final Runnable RECONCILE = BrightnessController::reconcileOnWorker;
    private static final Runnable QUERY_AND_RECONCILE = () -> {
        queryStateOnWorker();
        scheduleReconcile(CONFIRM_DELAY_MS);
    };

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
        Handler current = worker;
        if (current != null && BrightnessFeatureRuntime.isOperational() && !halted) {
            current.post(() -> {
                queryStateOnWorker();
                scheduleReconcile(CONFIRM_DELAY_MS);
            });
        }
    }

    static void onTopwayCallback(int command, int arg1, int arg2) {
        if (!STATE.acceptCallback(command, arg1, arg2)) return;
        transportReady = true;
        Handler current = worker;
        if (current != null && BrightnessFeatureRuntime.isOperational() && !halted) {
            current.removeCallbacks(RECONCILE);
            current.postDelayed(RECONCILE, 80L);
        }
    }

    static void onObservedWrite(int command, int arg1, int arg2) {
        if (command != BrightnessProtocol.COMMAND_MODE && command != BrightnessProtocol.COMMAND_BRIGHTNESS) return;
        Handler current = worker;
        Context currentContext = context;
        if (current == null || currentContext == null || halted) return;
        current.post(() -> {
            BrightnessPolicy.Config cfg = BrightnessConfig.read(currentContext);
            if (cfg.debug) XposedBridge.log("TS18Brightness: observed TW write command=" + command
                    + " arg1=" + arg1 + " arg2=" + arg2);
        });
    }

    static void stopMutationForProcess() {
        halted = true;
        Handler current = worker;
        if (current != null) current.removeCallbacksAndMessages(null);
    }

    private static void initialiseOnWorker(ClassLoader classLoader) {
        try {
            BrightnessCompatibility.Result result = BrightnessCompatibility.verify(context);
            if (!result.compatible) {
                BrightnessFeatureRuntime.markIncompatible(result.detail);
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
            registerConfigReceiverOnWorker();
            BrightnessFeatureRuntime.markCompatible();
            XposedBridge.log("TS18Brightness: exact SystemUI compatibility verified sha256="
                    + result.detail + "; waiting for stock TWSystemUI transport readiness; controller remains inert unless ts18_brightness_enabled=1");
            if (transportReady) {
                queryStateOnWorker();
                scheduleReconcile(CONFIRM_DELAY_MS);
            }
        } catch (Throwable t) {
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

    private static void registerConfigReceiverOnWorker() {
        if (configReceiverRegistered) return;
        configReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                String nonce = intent == null ? null : intent.getStringExtra(BrightnessConfig.EXTRA_NONCE);
                ResultReceiver resultReceiver = BrightnessConfig.resultReceiver(intent);
                try {
                    if (resultReceiver == null) {
                        throw new IllegalArgumentException("missing private result receiver");
                    }
                    BrightnessPolicy.Config requested = BrightnessConfig.fromRequest(intent);
                    if (requested.enabled && halted) {
                        throw new IllegalStateException(
                                "brightness circuit breaker is open; restart SystemUI before re-enabling");
                    }
                    BrightnessConfig.persistFromSystemUi(receiverContext, requested);
                    sendConfigResult(resultReceiver, nonce, true,
                            requested.enabled ? "Brightness policy applied." : "Brightness controller disabled; stock Topway behaviour is no longer rewritten.");
                    if (transportReady) scheduleReconcile(80L);
                } catch (Throwable t) {
                    XposedBridge.log("TS18Brightness: configuration bridge rejected request: " + t);
                    sendConfigResult(resultReceiver, nonce, false,
                            "Brightness configuration rejected: " + t.getClass().getSimpleName());
                }
            }
        };
        context.registerReceiver(configReceiver,
                new IntentFilter(BrightnessConfig.ACTION_APPLY),
                BrightnessConfig.CONFIGURE_PERMISSION, worker);
        configReceiverRegistered = true;
    }

    private static void sendConfigResult(ResultReceiver receiver, String nonce,
                                         boolean success, String detail) {
        if (receiver == null) return;
        try {
            Bundle data = new Bundle();
            data.putString(BrightnessConfig.EXTRA_NONCE, nonce);
            data.putBoolean(BrightnessConfig.EXTRA_SUCCESS, success);
            data.putString(BrightnessConfig.EXTRA_DETAIL, detail);
            receiver.send(success ? BrightnessConfig.RESULT_APPLIED : BrightnessConfig.RESULT_REJECTED, data);
        } catch (Throwable t) {
            XposedBridge.log("TS18Brightness: private configuration acknowledgement failed: " + t);
        }
    }

    private static void reconcileOnWorker() {
        if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational() || context == null) return;
        BrightnessPolicy.Config config = BrightnessConfig.read(context);
        if (!config.enabled) { resetAttempts(); return; }
        if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
            if (config.debug) XposedBridge.log("TS18Brightness: set-auto schedule invalid because day/night transition times are equal; no mutation");
            return;
        }

        BrightnessPolicy.State state = STATE.snapshot();
        if (!state.modeKnown || !state.levelsKnown) {
            queryStateOnWorker();
            scheduleReconcile(STATE_RETRY_MS);
            return;
        }

        int localMinute = currentLocalMinute();
        BrightnessPolicy.Action action = BrightnessPolicy.nextAction(config, state, localMinute);
        if (action.type == BrightnessPolicy.ActionType.NONE) {
            resetAttempts();
            scheduleNextTransitionOnWorker(config, localMinute);
            if (config.debug) logState("settled", config, state, localMinute);
            return;
        }
        if (!allowActionAttempt(action)) return;
        if (config.debug) XposedBridge.log("TS18Brightness: applying " + action.key()
                + " mode=" + config.mode.persisted + " localMinute=" + localMinute);
        postActionToMain(action);
        Handler current = worker;
        if (current != null) {
            current.removeCallbacks(QUERY_AND_RECONCILE);
            current.postDelayed(QUERY_AND_RECONCILE, CONFIRM_DELAY_MS);
        }
    }

    private static boolean allowActionAttempt(BrightnessPolicy.Action action) {
        String key = action.key();
        if (key.equals(lastActionKey)) sameActionAttempts++; else { lastActionKey = key; sameActionAttempts = 1; }
        if (sameActionAttempts <= MAX_SAME_ACTION_ATTEMPTS) return true;
        BrightnessFeatureRuntime.recordFailure("unconfirmed-action-" + key,
                new IllegalStateException("Topway state did not converge after " + MAX_SAME_ACTION_ATTEMPTS + " attempts"));
        return false;
    }

    private static void resetAttempts() { lastActionKey = null; sameActionAttempts = 0; }

    private static void postActionToMain(BrightnessPolicy.Action action) {
        MAIN.post(() -> {
            if (halted || !transportReady || !BrightnessFeatureRuntime.isOperational()) return;
            try {
                Object instance = getInstance.invoke(null);
                if (instance == null) throw new IllegalStateException("TWSystemUI singleton is null");
                switch (action.type) {
                    case SET_DAY_LEVEL:
                    case SET_NIGHT_LEVEL:
                        write3.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS, action.selector, action.value);
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
                BrightnessFeatureRuntime.recordFailure("topway-write", unwrap(t));
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
                write2.invoke(instance, BrightnessProtocol.COMMAND_MODE, BrightnessProtocol.QUERY_VALUE);
                write2.invoke(instance, BrightnessProtocol.COMMAND_BRIGHTNESS, BrightnessProtocol.QUERY_VALUE);
            } catch (Throwable t) {
                BrightnessFeatureRuntime.recordFailure("topway-query", unwrap(t));
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

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t.getCause();
        return cause == null ? t : cause;
    }
}
