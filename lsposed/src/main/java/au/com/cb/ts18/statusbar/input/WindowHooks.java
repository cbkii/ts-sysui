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
                        if (param.getThrowable() != null
                                || param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof View)
                                || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;

                        View root = (View) param.args[0];
                        ViewGroup.LayoutParams params =
                                (ViewGroup.LayoutParams) param.args[1];

                        if (StatusBarState.isStatusBarParams(params)) {
                            if (HookRuntime.isOperational()) handleCompatibilityStatusBarAdd(root, params);
                            return;
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.addView", addViewHook);

        XC_MethodHook.Unhook updateHook = XposedHelpers.findAndHookMethod(
                impl, "updateViewLayout", View.class, ViewGroup.LayoutParams.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null
                                || param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof View)
                                || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;

                        View view = (View) param.args[0];
                        ViewGroup.LayoutParams params =
                                (ViewGroup.LayoutParams) param.args[1];

                        if (StatusBarState.root() == view) {
                            if (HookRuntime.isOperational()) handleCompatibilityStatusBarUpdate(view, params);
                            return;
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.updateViewLayout", updateHook);
    }

    private static void handleCompatibilityStatusBarAdd(
            View root, ViewGroup.LayoutParams params) {
        try {
            Config.Snapshot config = Config.get(root.getContext());
            if (!config.enabled || !config.inputEnabled
                    || config.adapterMode != Config.AdapterMode.COMPATIBILITY) {
                StatusBarState.clear();
                return;
            }
            View previous = StatusBarState.capture(root, params);
            if (previous != root) SystemBarDimensions.clearCache();
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("window-add-view", t);
        }
    }

    private static void handleCompatibilityStatusBarUpdate(
            View view, ViewGroup.LayoutParams params) {
        try {
            StatusBarState.updateIfTracked(view, params);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("window-update-layout", t);
        }
    }

}
