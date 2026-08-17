package au.com.cb.ts18.statusbar.input;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class TouchableInsetsHook {
    private static volatile Field touchableRegionField;
    private static volatile Field touchableInsetsField;
    private static volatile Method setTouchableInsetsMethod;
    private static volatile Integer touchableInsetsRegionConstant;

    private TouchableInsetsHook() {}

    static void install(ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> observerClass = Class.forName("android.view.ViewTreeObserver", false, classLoader);
        XposedBridge.hookAllMethods(observerClass, "dispatchOnComputeInternalInsets", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (CircuitBreaker.isOpen() || param.args == null || param.args.length < 1) return;
                    View root = StatusBarState.root();
                    if (root == null || !root.isAttachedToWindow()) return;
                    ViewTreeObserver current = root.getViewTreeObserver();
                    if (current != param.thisObject) return;
                    apply(root, param.args[0]);
                } catch (Throwable t) {
                    CircuitBreaker.recordFailure("touchable insets", t);
                }
            }
        });
    }

    private static void apply(View root, Object info) throws Exception {
        Config.Snapshot cfg = Config.get(root.getContext());
        if (!cfg.enabled || !cfg.inputEnabled) return;
        if (isKeyguardLocked(root.getContext())) return;

        int barHeight = SystemBarDimensions.statusBarHeight(root);
        int width = root.getWidth();
        if (barHeight <= 0 || width <= 1) return;

        Class<?> infoClass = info.getClass();
        Field regionField = touchableRegionField;
        if (regionField == null || regionField.getDeclaringClass() != infoClass) {
            try { regionField = infoClass.getField("touchableRegion"); }
            catch (NoSuchFieldException e) {
                regionField = infoClass.getDeclaredField("touchableRegion");
                regionField.setAccessible(true);
            }
            touchableRegionField = regionField;
        }
        Region stock = (Region) regionField.get(info);
        if (stock == null) return;

        int mode = readTouchableInsetsMode(infoClass, info);
        int regionMode = resolveRegionMode(infoClass);
        Rect stockBounds = stock.getBounds();
        int trackedHeight = StatusBarState.windowHeightSpec();

        // Fail open while the shade/keyguard/transient region is clearly expanded.
        // A heads-up region extending materially below the compact bar is also kept stock.
        if (!stock.isEmpty() && stockBounds.bottom > (barHeight * 3) / 2) return;
        if (mode != regionMode && !(trackedHeight > 0 && trackedHeight <= barHeight * 2)) return;

        int rightInset = cfg.rightInsetOverridePx >= 0
                ? Math.min(cfg.rightInsetOverridePx, Math.max(0, width - 1))
                : SystemBarDimensions.rightSystemInset(root);
        TouchStripGeometry.Result geometry = TouchStripGeometry.compute(
                width, rightInset, cfg.touchFraction, cfg.cornerGapPx);

        // If the requested hard corner exclusion cannot be represented safely, leave stock
        // behaviour untouched rather than silently weakening the user's boundary.
        if (!geometry.valid) {
            RateLimitedLog.debug(cfg.debug,
                    "collapsed touch strip not applied: no safe region for width=" + width
                            + " insetRight=" + rightInset + " cornerGap=" + cfg.cornerGapPx);
            return;
        }

        Region result = new Region(geometry.stripLeft, 0, geometry.stripRight, barHeight);
        stock.set(result);

        Method setter = setTouchableInsetsMethod;
        if (setter == null || setter.getDeclaringClass() != infoClass) {
            try { setter = infoClass.getMethod("setTouchableInsets", int.class); }
            catch (NoSuchMethodException e) {
                setter = infoClass.getDeclaredMethod("setTouchableInsets", int.class);
                setter.setAccessible(true);
            }
            setTouchableInsetsMethod = setter;
        }
        setter.invoke(info, regionMode);

        RateLimitedLog.debug(cfg.debug,
                "collapsed touch strip x=" + geometry.stripLeft + ".." + geometry.stripRight
                        + " width=" + geometry.stripWidth() + "px bar=" + barHeight
                        + "px cornerGap=" + geometry.cornerGapPx
                        + "px insetRight=" + geometry.rightInset);
    }

    private static int readTouchableInsetsMode(Class<?> infoClass, Object info) {
        try {
            Field field = touchableInsetsField;
            if (field == null || field.getDeclaringClass() != infoClass) {
                field = infoClass.getDeclaredField("mTouchableInsets");
                field.setAccessible(true);
                touchableInsetsField = field;
            }
            return field.getInt(info);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int resolveRegionMode(Class<?> infoClass) {
        Integer cached = touchableInsetsRegionConstant;
        if (cached != null) return cached;
        try {
            Field f = infoClass.getField("TOUCHABLE_INSETS_REGION");
            int value = f.getInt(null);
            touchableInsetsRegionConstant = value;
            return value;
        } catch (Throwable ignored) {
            // Android 10 framework constant. Used only if reflection cannot read it.
            touchableInsetsRegionConstant = 3;
            return 3;
        }
    }

    private static boolean isKeyguardLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }
}
