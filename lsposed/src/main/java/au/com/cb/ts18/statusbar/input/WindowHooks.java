package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class WindowHooks {
    private WindowHooks() {}

    static void install(ClassLoader classLoader, HookRegistry registry) throws ClassNotFoundException {
        Class<?> impl = Class.forName("android.view.WindowManagerImpl", false, classLoader);

        Set<XC_MethodHook.Unhook> addViewHooks = XposedBridge.hookAllMethods(
                impl, "addView", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!HookRuntime.isOperational()
                                    || param.getThrowable() != null
                                    || param.args == null || param.args.length < 2) return;
                            if (!(param.args[0] instanceof View)
                                    || !StatusBarState.isStatusBarParams(param.args[1])) return;

                            View root = (View) param.args[0];
                            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) param.args[1];
                            View previous = StatusBarState.capture(root, params);
                            if (previous != null && previous != root) {
                                VisualScaler.detach(previous, true);
                                SystemBarDimensions.clearCache();
                            }
                            VisualScaler.attach(root);
                        } catch (Throwable t) {
                            CircuitBreaker.recordFailure("window-add-view", t);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.addView", addViewHooks);

        Set<XC_MethodHook.Unhook> updateHooks = XposedBridge.hookAllMethods(
                impl, "updateViewLayout", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!HookRuntime.isOperational()
                                    || param.getThrowable() != null
                                    || param.args == null || param.args.length < 2) return;
                            if (!(param.args[0] instanceof View)
                                    || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;
                            View view = (View) param.args[0];
                            if (StatusBarState.root() != view) return;
                            StatusBarState.updateIfTracked(view, (ViewGroup.LayoutParams) param.args[1]);
                        } catch (Throwable t) {
                            CircuitBreaker.recordFailure("window-update-layout", t);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.updateViewLayout", updateHooks);
    }
}
