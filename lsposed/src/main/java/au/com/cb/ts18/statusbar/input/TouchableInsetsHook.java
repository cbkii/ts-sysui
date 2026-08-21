package au.com.cb.ts18.statusbar.input;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;
import android.view.ViewTreeObserver;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class TouchableInsetsHook {
    private static final ThreadLocal<Rect> BOUNDS = ThreadLocal.withInitial(Rect::new);
    private TouchableInsetsHook() {}

    static void install(ClassLoader classLoader, HookRegistry registry) throws ClassNotFoundException {
        Class<?> observerClass = Class.forName("android.view.ViewTreeObserver", false, classLoader);
        Class<?> infoClass = Class.forName(
                "android.view.ViewTreeObserver$InternalInsetsInfo", false, classLoader);
        XC_MethodHook.Unhook registered = XposedHelpers.findAndHookMethod(
                observerClass, "dispatchOnComputeInternalInsets", infoClass, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!HookRuntime.isOperational()
                                    || param.args == null || param.args.length < 1) return;
                            View root = StatusBarState.root();
                            if (root == null || !root.isAttachedToWindow()) return;
                            ViewTreeObserver current = root.getViewTreeObserver();
                            if (current != param.thisObject) return;
                            apply(root, param.args[0]);
                        } catch (Throwable t) {
                            CircuitBreaker.recordFailure("touchable-insets", t);
                        }
                    }
                });
        registry.addRequired("ViewTreeObserver.dispatchOnComputeInternalInsets", registered);
    }

    private static void apply(View root, Object info) throws Exception {
        Config.Snapshot cfg = Config.get(root.getContext());
        if (!cfg.enabled || !cfg.inputEnabled
                || cfg.adapterMode != Config.AdapterMode.COMPATIBILITY) return;

        int barHeight = SystemBarDimensions.statusBarHeight(root);
        int width = root.getWidth();
        if (barHeight <= 0 || width <= 1) return;

        CoordinateSpaceVerifier.Snapshot coordinates = CoordinateSpaceVerifier.inspect(root);
        if (!coordinates.valid) {
            RateLimitedLog.debug(cfg.debug,
                    "input kept stock: coordinate-space=" + coordinates.reason
                            + " root=(" + coordinates.rootX + "," + coordinates.rootY + ")"
                            + " width=" + coordinates.rootWidth
                            + " physicalWidth=" + coordinates.physicalWidth);
            return;
        }

        InternalInsetsAccess.Snapshot insets = InternalInsetsAccess.read(info);
        Region stock = insets.region;
        if (stock == null) return;

        int mode = insets.mode;
        int regionMode = insets.regionMode;
        Rect stockBounds = BOUNDS.get();
        stock.getBounds(stockBounds);
        boolean keyguardLocked = isKeyguardLocked(root.getContext());

        TouchableStatePolicy.Decision state = TouchableStatePolicy.evaluate(
                keyguardLocked,
                stock.isEmpty(),
                stock.isRect(),
                stockBounds.left,
                stockBounds.top,
                stockBounds.right,
                stockBounds.bottom,
                width,
                barHeight,
                mode,
                regionMode,
                StatusBarState.windowHeightSpec());
        if (!state.apply) {
            RateLimitedLog.debug(cfg.debug, "input kept stock: state=" + state.reason
                    + " region=" + stockBounds + " mode=" + mode
                    + " windowHeight=" + StatusBarState.windowHeightSpec()
                    + " barHeight=" + barHeight);
            return;
        }

        int rightInset = cfg.rightInsetOverridePx >= 0
                ? Math.min(cfg.rightInsetOverridePx, Math.max(0, width - 1))
                : SystemBarDimensions.rightSystemInset(root);
        TouchStripGeometry.Result geometry = TouchStripGeometry.compute(
                width, rightInset, cfg.touchFraction, cfg.cornerGapPx);

        if (!geometry.valid) {
            RateLimitedLog.debug(cfg.debug,
                    "input kept stock: no safe strip for width=" + width
                            + " insetRight=" + rightInset + " cornerGap=" + cfg.cornerGapPx);
            return;
        }

        // Stock already selected TOUCHABLE_INSETS_REGION; mutate that existing Region directly.
        // No setter reflection or temporary Region allocation is needed on this hot path.
        stock.set(geometry.stripLeft, 0, geometry.stripRight, barHeight);

        RateLimitedLog.debug(cfg.debug,
                "collapsed touch strip x=" + geometry.stripLeft + ".." + geometry.stripRight
                        + " width=" + geometry.stripWidth() + "px bar=" + barHeight
                        + "px cornerGap=" + geometry.cornerGapPx
                        + "px insetRight=" + geometry.rightInset);
    }

    private static boolean isKeyguardLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }
}
