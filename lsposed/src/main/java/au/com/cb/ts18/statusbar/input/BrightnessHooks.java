package au.com.cb.ts18.statusbar.input;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Optional private-SystemUI hooks. Any mismatch disables brightness only. */
final class BrightnessHooks {
    private static volatile int installedCount;

    private BrightnessHooks() {}

    static int installSafely(ClassLoader classLoader) {
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        DiagnosticJournal.state("brightness-hook-set", "INSTALLING",
                "SystemUIApplication/TW callback/TW init/TW write");
        try {
            Class<?> appClass = Class.forName("com.android.systemui.SystemUIApplication", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getThrowable() != null || !(param.thisObject instanceof Context)) return;
                    DiagnosticJournal.record("INFO", "brightness-app-lifecycle",
                            "SystemUIApplication.onCreate observed");
                    BrightnessController.attach((Context) param.thisObject, classLoader);
                }
            }));
            DiagnosticJournal.state("brightness-hook-app", "INSTALLED",
                    "SystemUIApplication.onCreate");

            Class<?> callbackClass = Class.forName("com.android.systemui.tw.StatusBarViewInit", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(callbackClass, "sendTWCallBack",
                    int.class, int.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.args == null || param.args.length != 3) return;
                            int command = (Integer) param.args[0];
                            if (BuildConfig.TS18_DIAGNOSTIC
                                    && (command == BrightnessProtocol.COMMAND_MODE
                                    || command == BrightnessProtocol.COMMAND_BRIGHTNESS)) {
                                DiagnosticJournal.record("DEBUG", "brightness-callback",
                                        "command=" + command + " arg1=" + param.args[1]
                                                + " arg2=" + param.args[2]);
                            }
                            BrightnessController.onTopwayCallback(command,
                                    (Integer) param.args[1], (Integer) param.args[2]);
                        }
                    }));
            DiagnosticJournal.state("brightness-hook-callback", "INSTALLED",
                    "StatusBarViewInit.sendTWCallBack(int,int,int)");

            Class<?> twClass = Class.forName("com.android.systemui.tw.TWSystemUI", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(twClass, "init", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getThrowable() == null) {
                        DiagnosticJournal.record("INFO", "brightness-transport",
                                "stock TWSystemUI.init completed");
                        BrightnessController.onTransportReady();
                    } else {
                        DiagnosticJournal.failure("brightness-transport",
                                "stock TWSystemUI.init returned throwable", param.getThrowable());
                    }
                }
            }));
            DiagnosticJournal.state("brightness-hook-init", "INSTALLED", "TWSystemUI.init");

            installed.add(XposedHelpers.findAndHookMethod(twClass, "write",
                    int.class, int.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.args == null || param.args.length != 3) return;
                            int command = (Integer) param.args[0];
                            if (BuildConfig.TS18_DIAGNOSTIC
                                    && (command == BrightnessProtocol.COMMAND_MODE
                                    || command == BrightnessProtocol.COMMAND_BRIGHTNESS)) {
                                DiagnosticJournal.record("DEBUG", "brightness-write-observed",
                                        "command=" + command + " arg1=" + param.args[1]
                                                + " arg2=" + param.args[2]);
                            }
                            BrightnessController.onObservedWrite(command,
                                    (Integer) param.args[1], (Integer) param.args[2]);
                        }
                    }));
            DiagnosticJournal.state("brightness-hook-write", "INSTALLED",
                    "TWSystemUI.write(int,int,int)");

            for (XC_MethodHook.Unhook hook : installed) {
                if (hook == null) throw new IllegalStateException("brightness hook registration returned null");
            }
            installedCount = installed.size();
            DiagnosticJournal.state("brightness-hook-set", "INSTALLED",
                    "count=" + installedCount);
            RateLimitedLog.event("brightness-hooks", installed.size()
                    + " observation/lifecycle hooks installed; exact-binary verification and persistent enable gate mutation");
            return installed.size();
        } catch (Throwable t) {
            installedCount = 0;
            for (int i = installed.size() - 1; i >= 0; i--) {
                try { if (installed.get(i) != null) installed.get(i).unhook(); }
                catch (Throwable ignored) { }
            }
            DiagnosticJournal.failure("brightness-hook-set",
                    "hook installation failed; partial hooks removed", t);
            DiagnosticJournal.state("brightness-hook-set", "FAILED",
                    t.getClass().getSimpleName());
            BrightnessFeatureRuntime.markIncompatible("private hook contract mismatch: "
                    + t.getClass().getSimpleName());
            RateLimitedLog.error("brightness-hooks",
                    "hook installation failed open without affecting compact/nav features", t);
            return 0;
        }
    }

    static int installedCount() {
        return installedCount;
    }
}
