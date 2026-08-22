package au.com.cb.ts18.statusbar.input;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Exact Android-Q/TS18 adapter for the SystemUI touch-region authority. */
final class ExactTs18TouchableRegionAdapter {
    private static final Map<View, Binding> ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Members members;

    private ExactTs18TouchableRegionAdapter() {}

    static boolean installSafely(ClassLoader classLoader, HookRegistry registry) {
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            Members resolved = Members.resolve(classLoader);
            members = resolved;

            Object[] constructorContract = new Object[] {
                    Context.class,
                    resolved.headsUpClass,
                    resolved.statusBarClass,
                    View.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.thisObject == null) return;
                            syncListener(param.thisObject);
                        }
                    }
            };
            installed.add(XposedHelpers.findAndHookConstructor(
                    resolved.managerClass, constructorContract));

            installed.add(XposedHelpers.findAndHookMethod(
                    resolved.managerClass, "updateTouchableRegion", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null || param.thisObject == null) return;
                            // Stock may add/remove its own listener here. Re-add ours afterwards
                            // so the module's one listener remains ordered after stock.
                            syncListener(param.thisObject);
                        }
                    }));

            for (int i = 0; i < installed.size(); i++) {
                registry.addRequired("exact touch adapter " + i, installed.get(i));
            }
            RateLimitedLog.always(
                    "exact TS18 touch contract installed; one post-stock listener awaits hash and policy gates");
            return true;
        } catch (Throwable t) {
            for (int i = installed.size() - 1; i >= 0; i--) {
                try {
                    if (installed.get(i) != null) installed.get(i).unhook();
                } catch (Throwable ignored) {
                    // The feature remains inert even if an incomplete optional install cannot unhook.
                }
            }
            members = null;
            RateLimitedLog.error("exact-touch-contract",
                    "exact adapter contract mismatch; no exact touch mutation installed", t);
            return false;
        }
    }

    static void failOpen() {
        List<Map.Entry<View, Binding>> bindings;
        synchronized (ROOTS) {
            bindings = new ArrayList<>(ROOTS.entrySet());
            ROOTS.clear();
        }
        for (Map.Entry<View, Binding> entry : bindings) {
            View root = entry.getKey();
            if (root == null) continue;
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) {
                    Members contract = members;
                    if (contract != null) {
                        contract.removeInsetsListener.invoke(observer, entry.getValue().listener);
                    }
                }
            } catch (Throwable t) {
                RateLimitedLog.error("exact-touch-detach",
                        "failed to detach one exact touch listener", t);
            }
        }
    }

    private static void syncListener(Object manager) {
        try {
            Members contract = members;
            if (contract == null) return;
            View root = (View) contract.statusBarWindowView.get(manager);
            if (root == null) return;
            ExactSystemUiIdentity.start(root.getContext());

            Binding previous;
            synchronized (ROOTS) {
                previous = ROOTS.remove(root);
            }
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            if (previous != null) {
                try {
                    contract.removeInsetsListener.invoke(observer, previous.listener);
                } catch (Throwable ignored) {
                    // Re-adding below remains idempotent for the current observer generation.
                }
            }

            WeakReference<Object> managerRef = new WeakReference<>(manager);
            Object listener = Proxy.newProxyInstance(
                    contract.insetsListenerClass.getClassLoader(),
                    new Class<?>[] { contract.insetsListenerClass },
                    (proxy, method, args) -> {
                        if ("onComputeInternalInsets".equals(method.getName())
                                && args != null && args.length == 1) {
                            Object current = managerRef.get();
                            if (current != null) apply(current, args[0]);
                            return null;
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        if ("toString".equals(method.getName())) {
                            return "TS18ExactInsetsListener";
                        }
                        return null;
                    });
            contract.addInsetsListener.invoke(observer, listener);
            synchronized (ROOTS) {
                ROOTS.put(root, new Binding(listener));
            }
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("exact-touch-listener", t);
        }
    }

    private static void apply(Object manager, Object info) {
        try {
            if (!HookRuntime.isOperational()) return;
            Members contract = members;
            if (contract == null) return;
            View root = (View) contract.statusBarWindowView.get(manager);
            if (root == null) return;
            Config.Snapshot config = Config.get(root.getContext());
            if (!config.enabled || !config.inputEnabled
                    || config.adapterMode != Config.AdapterMode.EXACT) return;

            boolean expanded = contract.isStatusBarExpanded.getBoolean(manager);
            boolean forceCollapsed = contract.forceCollapsedUntilLayout.getBoolean(manager);
            boolean stockShouldAdjustInsets = contract.shouldAdjustInsets.getBoolean(manager);
            Object statusBar = contract.statusBar.get(manager);
            Object headsUp = contract.headsUpManager.get(manager);
            Object bubbles = contract.bubbleController.get(manager);
            if (statusBar == null || headsUp == null || bubbles == null) return;

            ExactTouchSafetyPolicy.Decision safety = ExactTouchSafetyPolicy.evaluate(
                    ExactSystemUiIdentity.isSupported(),
                    root.isAttachedToWindow(),
                    expanded,
                    isKeyguardLocked(root.getContext()),
                    (Boolean) contract.isBouncerShowing.invoke(statusBar),
                    (Boolean) contract.hasPinnedHeadsUp.invoke(headsUp),
                    (Boolean) contract.isHeadsUpGoingAway.invoke(headsUp),
                    (Boolean) contract.hasBubbles.invoke(bubbles),
                    forceCollapsed,
                    stockShouldAdjustInsets);
            if (!safety.apply) {
                RateLimitedLog.debug(config.debug,
                        "exact input kept stock: state=" + safety.reason
                                + " identity=" + ExactSystemUiIdentity.state()
                                + '/' + ExactSystemUiIdentity.detail());
                return;
            }

            int barHeight = contract.statusBarHeight.getInt(manager);
            int width = root.getWidth();
            if (barHeight <= 0 || width <= 1) return;

            CoordinateSpaceVerifier.Snapshot coordinates = CoordinateSpaceVerifier.inspect(root);
            if (!coordinates.valid) {
                RateLimitedLog.debug(config.debug,
                        "exact input kept stock: coordinate-space=" + coordinates.reason);
                return;
            }

            int rightInset = config.rightInsetOverridePx >= 0
                    ? Math.min(config.rightInsetOverridePx, Math.max(0, width - 1))
                    : SystemBarDimensions.rightSystemInset(root);
            TouchStripGeometry.Result geometry = TouchStripGeometry.compute(
                    width, rightInset, config.touchFraction, config.cornerGapPx);
            if (!geometry.valid) return;

            // Ordinary Android-Q computation starts in FRAME mode with an empty
            // Region. Exact mode deliberately establishes REGION here. Stock
            // special-region ownership has already been excluded by
            // mShouldAdjustInsets and the explicit state gates above.
            InternalInsetsAccess.setTouchableRegion(info,
                    geometry.stripLeft, 0, geometry.stripRight, barHeight);
            RateLimitedLog.debug(config.debug,
                    "exact collapsed touch FRAME/stock -> REGION x=" + geometry.stripLeft + ".."
                            + geometry.stripRight + " bar=" + barHeight
                            + " insetRight=" + geometry.rightInset);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("exact-touch-apply", t);
        }
    }

    private static boolean isKeyguardLocked(Context context) {
        KeyguardManager manager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }

    private static final class Binding {
        final Object listener;

        Binding(Object listener) {
            this.listener = listener;
        }
    }

    private static final class Members {
        final Class<?> managerClass;
        final Class<?> infoClass;
        final Class<?> headsUpClass;
        final Class<?> statusBarClass;
        final Class<?> insetsListenerClass;
        final Field statusBarWindowView;
        final Field statusBarHeight;
        final Field isStatusBarExpanded;
        final Field forceCollapsedUntilLayout;
        final Field shouldAdjustInsets;
        final Field statusBar;
        final Field headsUpManager;
        final Field bubbleController;
        final Method isBouncerShowing;
        final Method hasPinnedHeadsUp;
        final Method isHeadsUpGoingAway;
        final Method hasBubbles;
        final Method addInsetsListener;
        final Method removeInsetsListener;

        Members(Class<?> managerClass,
                Class<?> infoClass,
                Class<?> headsUpClass,
                Class<?> statusBarClass,
                Class<?> insetsListenerClass,
                Field statusBarWindowView,
                Field statusBarHeight,
                Field isStatusBarExpanded,
                Field forceCollapsedUntilLayout,
                Field shouldAdjustInsets,
                Field statusBar,
                Field headsUpManager,
                Field bubbleController,
                Method isBouncerShowing,
                Method hasPinnedHeadsUp,
                Method isHeadsUpGoingAway,
                Method hasBubbles,
                Method addInsetsListener,
                Method removeInsetsListener) {
            this.managerClass = managerClass;
            this.infoClass = infoClass;
            this.headsUpClass = headsUpClass;
            this.statusBarClass = statusBarClass;
            this.insetsListenerClass = insetsListenerClass;
            this.statusBarWindowView = statusBarWindowView;
            this.statusBarHeight = statusBarHeight;
            this.isStatusBarExpanded = isStatusBarExpanded;
            this.forceCollapsedUntilLayout = forceCollapsedUntilLayout;
            this.shouldAdjustInsets = shouldAdjustInsets;
            this.statusBar = statusBar;
            this.headsUpManager = headsUpManager;
            this.bubbleController = bubbleController;
            this.isBouncerShowing = isBouncerShowing;
            this.hasPinnedHeadsUp = hasPinnedHeadsUp;
            this.isHeadsUpGoingAway = isHeadsUpGoingAway;
            this.hasBubbles = hasBubbles;
            this.addInsetsListener = addInsetsListener;
            this.removeInsetsListener = removeInsetsListener;
        }

        static Members resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> manager = Class.forName(
                    "com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager",
                    false, loader);
            Class<?> info = Class.forName(
                    "android.view.ViewTreeObserver$InternalInsetsInfo", false, loader);
            Class<?> insetsListener = Class.forName(
                    "android.view.ViewTreeObserver$OnComputeInternalInsetsListener",
                    false, loader);
            Class<?> heads = Class.forName(
                    "com.android.systemui.statusbar.phone.HeadsUpManagerPhone", false, loader);
            Class<?> status = Class.forName(
                    "com.android.systemui.statusbar.phone.StatusBar", false, loader);
            Class<?> bubbles = Class.forName(
                    "com.android.systemui.bubbles.BubbleController", false, loader);

            Constructor<?> constructor = manager.getDeclaredConstructor(
                    Context.class, heads, status, View.class);
            if (constructor == null) throw new NoSuchMethodException("touch manager constructor");
            // Keep the exact stock method in the contract, but do not hook it as a
            // second mutation path. The persistent listener below is the sole owner.
            requireMethod(manager, "onComputeInternalInsets", void.class, info);
            requireMethod(manager, "updateTouchableRegion", void.class);

            Field root = requireField(manager, "mStatusBarWindowView", View.class);
            Field height = requireField(manager, "mStatusBarHeight", int.class);
            Field expanded = requireField(manager, "mIsStatusBarExpanded", boolean.class);
            Field force = requireField(manager, "mForceCollapsedUntilLayout", boolean.class);
            // This Android-Q stock ownership signal is runtime-required. If this
            // exact installed binary does not expose it with the expected type,
            // the exact adapter fails open instead of guessing special states.
            Field shouldAdjust = requireField(manager, "mShouldAdjustInsets", boolean.class);
            Field statusField = requireField(manager, "mStatusBar", status);
            Field headsField = requireField(manager, "mHeadsUpManager", heads);
            Field bubblesField = requireField(manager, "mBubbleController", bubbles);

            Method bouncer = requireMethod(status, "isBouncerShowing", boolean.class);
            Method pinned = requireMethod(heads, "hasPinnedHeadsUp", boolean.class);
            Method goingAway = requireMethod(heads, "isHeadsUpGoingAway", boolean.class);
            Method hasBubbles = requireMethod(bubbles, "hasBubbles", boolean.class);
            requireMethod(bubbles, "getTouchableRegion", Rect.class);
            Method addListener = requireMethod(ViewTreeObserver.class,
                    "addOnComputeInternalInsetsListener", void.class, insetsListener);
            Method removeListener = requireMethod(ViewTreeObserver.class,
                    "removeOnComputeInternalInsetsListener", void.class, insetsListener);

            return new Members(manager, info, heads, status, insetsListener,
                    root, height, expanded, force, shouldAdjust,
                    statusField, headsField, bubblesField, bouncer, pinned, goingAway,
                    hasBubbles, addListener, removeListener);
        }

        private static Field requireField(Class<?> owner, String name, Class<?> type)
                throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != type) {
                throw new NoSuchFieldException(owner.getName() + '.' + name + " type mismatch");
            }
            field.setAccessible(true);
            return field;
        }

        private static Method requireMethod(Class<?> owner, String name, Class<?> returnType,
                                            Class<?>... parameters)
                throws ReflectiveOperationException {
            Method method = null;
            Class<?> current = owner;
            while (current != null && method == null) {
                try {
                    method = current.getDeclaredMethod(name, parameters);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            if (method == null) {
                throw new NoSuchMethodException(owner.getName() + '.' + name);
            }
            if (method.getReturnType() != returnType) {
                throw new NoSuchMethodException(owner.getName() + '.' + name
                        + " return mismatch");
            }
            method.setAccessible(true);
            return method;
        }
    }
}
