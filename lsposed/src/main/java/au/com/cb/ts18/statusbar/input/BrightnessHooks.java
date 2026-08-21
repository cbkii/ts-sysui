package au.com.cb.ts18.statusbar.input;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional private-SystemUI hooks. Any mismatch disables brightness only. */
final class BrightnessHooks {
    private BrightnessHooks() {}

    static int installSafely(ClassLoader classLoader) {
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            Class<?> appClass = Class.forName("com.android.systemui.SystemUIApplication", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getThrowable() != null || !(param.thisObject instanceof Context)) return;
                    BrightnessController.attach((Context) param.thisObject, classLoader);
                }
            }));

            Class<?> callbackClass = Class.forName("com.android.systemui.tw.StatusBarViewInit", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(callbackClass, "sendTWCallBack",
                    int.class, int.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.args == null || param.args.length != 3) return;
                            BrightnessController.onTopwayCallback((Integer) param.args[0],
                                    (Integer) param.args[1], (Integer) param.args[2]);
                        }
                    }));

            Class<?> twClass = Class.forName("com.android.systemui.tw.TWSystemUI", false, classLoader);
            installed.add(XposedHelpers.findAndHookMethod(twClass, "init", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getThrowable() == null) BrightnessController.onTransportReady();
                }
            }));
            installed.add(XposedHelpers.findAndHookMethod(twClass, "write",
                    int.class, int.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.args == null || param.args.length != 3) return;
                            BrightnessController.onObservedWrite((Integer) param.args[0],
                                    (Integer) param.args[1], (Integer) param.args[2]);
                        }
                    }));

            for (XC_MethodHook.Unhook hook : installed) {
                if (hook == null) throw new IllegalStateException("brightness hook registration returned null");
            }
            XposedBridge.log("TS18Brightness: " + installed.size()
                    + " observation/lifecycle hooks installed; exact-binary verification and persistent enable gate mutation");
            return installed.size();
        } catch (Throwable t) {
            for (int i = installed.size() - 1; i >= 0; i--) {
                try { if (installed.get(i) != null) installed.get(i).unhook(); }
                catch (Throwable ignored) { }
            }
            BrightnessFeatureRuntime.markIncompatible("private hook contract mismatch: "
                    + t.getClass().getSimpleName());
            XposedBridge.log("TS18Brightness: hook installation failed open without affecting compact/nav features: " + t);
            return 0;
        }
    }
}
