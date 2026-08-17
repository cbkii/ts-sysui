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
                        if (!HookRuntime.isOperational()
                                || param.getThrowable() != null
                                || param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof View)
                                || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;

                        View root = (View) param.args[0];
                        ViewGroup.LayoutParams params =
                                (ViewGroup.LayoutParams) param.args[1];

                        if (StatusBarState.isStatusBarParams(params)) {
                            handleStatusBarAdd(root, params);
                            return;
                        }
                        if (NavBarState.isNavigationBarParams(params)) {
                            handleNavigationBarAdd(root, params);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.addView", addViewHook);

        XC_MethodHook.Unhook updateHook = XposedHelpers.findAndHookMethod(
                impl, "updateViewLayout", View.class, ViewGroup.LayoutParams.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!HookRuntime.isOperational()
                                || param.getThrowable() != null
                                || param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof View)
                                || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;

                        View view = (View) param.args[0];
                        ViewGroup.LayoutParams params =
                                (ViewGroup.LayoutParams) param.args[1];

                        if (StatusBarState.root() == view) {
                            handleStatusBarUpdate(view, params);
                            return;
                        }
                        if (NavBarState.root() == view
                                && NavBarState.isNavigationBarParams(params)) {
                            handleNavigationBarUpdate(view, params);
                        }
                    }
                });
        registry.addRequired("WindowManagerImpl.updateViewLayout", updateHook);
    }

    private static void handleStatusBarAdd(View root, ViewGroup.LayoutParams params) {
        try {
            View previous = StatusBarState.capture(root, params);
            if (previous != null && previous != root) {
                VisualScaler.detach(previous, true);
                SystemBarDimensions.clearCache();
            }
            VisualScaler.sync(root);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("window-add-view", t);
        }
    }

    private static void handleStatusBarUpdate(View view, ViewGroup.LayoutParams params) {
        try {
            StatusBarState.updateIfTracked(view, params);
            VisualScaler.sync(view);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("window-update-layout", t);
        }
    }

    private static void handleNavigationBarAdd(View root, ViewGroup.LayoutParams params) {
        if (!NavFeatureRuntime.isOperational()) return;
        try {
            NavBarState.Capture capture = NavBarState.capture(root, params);
            if (capture.previous != null && capture.previous != root) {
                NavHierarchyProbe.detach(capture.previous);
            }
            NavHierarchyProbe.sync(root, capture.generation);
        } catch (Throwable t) {
            NavFeatureRuntime.recordFailure("window-add-view", t);
        }
    }

    private static void handleNavigationBarUpdate(View view, ViewGroup.LayoutParams params) {
        if (!NavFeatureRuntime.isOperational()) return;
        try {
            NavBarState.updateIfTracked(view, params);
            NavHierarchyProbe.sync(view, NavBarState.generation());
        } catch (Throwable t) {
            NavFeatureRuntime.recordFailure("window-update-layout", t);
        }
    }
}
