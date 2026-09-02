package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Non-blocking runtime verification of the installed exact SystemUI APK. */
final class ExactSystemUiIdentity {
    enum State { UNCHECKED, CHECKING, SUPPORTED, UNSUPPORTED }

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile State state = State.UNCHECKED;
    private static volatile String detail = "not-checked";
    private static final List<Runnable> listeners = new ArrayList<>();

    private ExactSystemUiIdentity() {}

    static void install(ClassLoader classLoader, HookRegistry registry)
            throws ClassNotFoundException {
        DiagnosticJournal.state("identity-hook", "RESOLVING",
                "SystemUIApplication.onCreate");
        Class<?> application = Class.forName(
                "com.android.systemui.SystemUIApplication", false, classLoader);
        XC_MethodHook.Unhook hook = XposedHelpers.findAndHookMethod(
                application, "onCreate", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null || !(param.thisObject instanceof Context)) {
                            DiagnosticJournal.record("DEBUG", "systemui-oncreate",
                                    "ignored throwable/non-Context callback");
                            return;
                        }
                        DiagnosticJournal.state("systemui-oncreate", "SEEN",
                                "bridge install + identity verification requested");
                        SystemUiBridge.install((Context) param.thisObject);
                        start((Context) param.thisObject);
                    }
                });
        registry.addRequired("SystemUIApplication.onCreate identity gate", hook);
        DiagnosticJournal.state("identity-hook", "INSTALLED",
                "SystemUIApplication.onCreate");
    }

    static void start(Context context) {
        if (context == null) {
            DiagnosticJournal.state("identity", "BLOCKED", "null context");
            return;
        }
        if (!STARTED.compareAndSet(false, true)) {
            DiagnosticJournal.record("DEBUG", "identity",
                    "verification already started state=" + state);
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;
        Context verificationContext = appContext;
        state = State.CHECKING;
        detail = "hashing";
        DiagnosticJournal.state("identity", "CHECKING",
                "API=" + Build.VERSION.SDK_INT + " device=" + Build.DEVICE
                        + " product=" + Build.PRODUCT);
        RateLimitedLog.event("identity", "exact SystemUI APK verification started off main");

        Thread worker = new Thread(() -> verify(verificationContext),
                "TS18-SystemUI-contract");
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (Throwable t) {
            state = State.UNSUPPORTED;
            detail = "worker-start-" + t.getClass().getSimpleName();
            DiagnosticJournal.state("identity", "UNSUPPORTED", detail);
            notifyResolved();
            RateLimitedLog.error("contract-worker",
                    "exact SystemUI identity verification could not start; mutation stays off", t);
        }
    }

    static boolean isSupported() {
        return state == State.SUPPORTED;
    }

    static State state() {
        return state;
    }

    static String detail() {
        return detail;
    }

    static void whenResolved(Runnable listener) {
        if (listener == null) return;
        boolean runNow;
        synchronized (listeners) {
            runNow = state == State.SUPPORTED || state == State.UNSUPPORTED;
            if (!runNow) listeners.add(listener);
        }
        DiagnosticJournal.record("DEBUG", "identity-listener",
                runNow ? "resolution already available; dispatch now"
                        : "listener queued while state=" + state);
        if (runNow) dispatchListener(listener);
    }

    private static void verify(Context context) {
        try {
            String sourceDir = context.getApplicationInfo().sourceDir;
            if (sourceDir == null || sourceDir.trim().isEmpty()) {
                reject("source-dir");
                return;
            }
            DiagnosticJournal.record("DEBUG", "identity",
                    "hash source=" + sourceDir);
            String actual = sha256(sourceDir);
            SystemUiContractPolicy.Decision decision = SystemUiContractPolicy.evaluate(
                    Build.VERSION.SDK_INT, Build.DEVICE, Build.PRODUCT, actual);
            if (!decision.supported) {
                reject(decision.reason + ':' + actual);
                return;
            }
            detail = actual;
            state = State.SUPPORTED;
            DiagnosticJournal.state("identity", "SUPPORTED", actual);
            RateLimitedLog.event("identity",
                    "exact SystemUI contract verified asynchronously: " + actual);
            notifyResolved();
        } catch (Throwable t) {
            reject("hash-" + t.getClass().getSimpleName());
            RateLimitedLog.error("contract-hash",
                    "exact SystemUI identity verification failed; mutation stays off", t);
        }
    }

    private static void reject(String reason) {
        detail = reason;
        state = State.UNSUPPORTED;
        DiagnosticJournal.state("identity", "UNSUPPORTED", reason);
        RateLimitedLog.event("identity", "STOP: unsupported SystemUI contract; " + reason);
        notifyResolved();
    }

    private static void notifyResolved() {
        List<Runnable> pending;
        synchronized (listeners) {
            pending = new ArrayList<>(listeners);
            listeners.clear();
        }
        DiagnosticJournal.record("DEBUG", "identity-listener",
                "dispatching count=" + pending.size() + " state=" + state);
        for (Runnable listener : pending) dispatchListener(listener);
    }

    /** Every resolution callback that can touch SystemUI/View state runs on main. */
    private static void dispatchListener(Runnable listener) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runListenerNow(listener);
            return;
        }
        boolean posted;
        try {
            posted = MAIN.post(() -> runListenerNow(listener));
        } catch (Throwable t) {
            RateLimitedLog.error("contract-listener-dispatch",
                    "exact SystemUI resolution callback could not be posted to main", t);
            return;
        }
        if (!posted) {
            DiagnosticJournal.state("identity-listener", "BLOCKED",
                    "main looper rejected post");
            RateLimitedLog.event("identity-listener",
                    "exact SystemUI resolution callback was not accepted by the main looper; mutation stays fail-open until another lifecycle event");
        }
    }

    private static void runListenerNow(Runnable listener) {
        try {
            listener.run();
        } catch (Throwable t) {
            RateLimitedLog.error("contract-listener",
                    "exact SystemUI resolution listener failed", t);
        }
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }
}
