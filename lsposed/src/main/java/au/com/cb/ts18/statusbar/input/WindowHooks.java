package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class WindowHooks {
    private WindowHooks() {}

    static void install(ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> impl = Class.forName("android.view.WindowManagerImpl", false, classLoader);

        XposedBridge.hookAllMethods(impl, "addView", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (CircuitBreaker.isOpen() || param.args == null || param.args.length < 2) return;
                    if (!(param.args[0] instanceof View) || !StatusBarState.isStatusBarParams(param.args[1])) return;
                    View root = (View) param.args[0];
                    ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[1];
                    normaliseHeightIfRequested(root, lp);
                    StatusBarState.capture(root, lp);
                } catch (Throwable t) {
                    CircuitBreaker.recordFailure("WindowManager.addView", t);
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    View root = StatusBarState.root();
                    if (root != null) VisualScaler.attach(root);
                } catch (Throwable t) {
                    CircuitBreaker.recordFailure("visual attach", t);
                }
            }
        });

        XposedBridge.hookAllMethods(impl, "updateViewLayout", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (CircuitBreaker.isOpen() || param.args == null || param.args.length < 2) return;
                    if (!(param.args[0] instanceof View) || !(param.args[1] instanceof ViewGroup.LayoutParams)) return;
                    View view = (View) param.args[0];
                    View root = StatusBarState.root();
                    if (root == null || root != view) return;
                    ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[1];
                    normaliseHeightIfRequested(root, lp);
                    StatusBarState.updateIfTracked(view, lp);
                } catch (Throwable t) {
                    CircuitBreaker.recordFailure("WindowManager.updateViewLayout", t);
                }
            }
        });
    }

    private static void normaliseHeightIfRequested(View root, ViewGroup.LayoutParams lp) {
        Config.Snapshot cfg = Config.get(root.getContext());
        if (!cfg.enabled || !cfg.normaliseWindowHeight) return;
        if (!(lp instanceof WindowManager.LayoutParams)) return;
        WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
        if (wlp.type != WindowManager.LayoutParams.TYPE_STATUS_BAR) return;
        if (lp.height <= 0) return; // MATCH_PARENT/WRAP_CONTENT: do not guess.
        int expected = SystemBarDimensions.statusBarHeight(root);
        if (expected > 0 && lp.height != expected) lp.height = expected;
    }
}
