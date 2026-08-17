package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class WindowHooks {
    private WindowHooks() {}

    static void install(ClassLoader classLoader, HookRegistry registry) throws ClassNotFoundException {
        Class<?> impl = Class.forName("android.view.WindowManagerImpl", false, classLoader);

        XC_MethodHook.Unhook addViewHook = XposedHelpers.findAndHookMethod(
                impl, "addView", View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
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
                            // Do not keep a layout listener alive in observation-only mode.
                            VisualScaler.sync(root);
                        } catch (Throwable t) {
                            CircuitBreaker.recordFailure("window-add-view", t);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.addView", addViewHook);

        XC_MethodHook.Unhook updateHook = XposedHelpers.findAndHookMethod(
                impl, "updateViewLayout", View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!HookRuntime.isOperational()
                                    || param.getThrowable() != null
                                    || param.args == null || param.args.length < 2) return;
                            if (!(param.args[0] instanceof View)
                                    || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;
                            View view = (View) param.args[0];
                            if (StatusBarState.root() != view) return;
                            StatusBarState.updateIfTracked(
                                    view, (ViewGroup.LayoutParams) param.args[1]);
                            VisualScaler.sync(view);
                        } catch (Throwable t) {
                            CircuitBreaker.recordFailure("window-update-layout", t);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.updateViewLayout", updateHook);
    }
}
