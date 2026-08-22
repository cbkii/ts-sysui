package au.com.cb.ts18.statusbar.input;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps module-owned media controls subordinate to stock Topway panel visibility.
 * It observes public View lifecycle only; it does not hook reverse/MCU commands or
 * ever force the OEM panel visible.
 */
final class ExactTopwayNavVisibilityMonitor {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final Map<View, Monitor> MONITORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ExactTopwayNavVisibilityMonitor() {}

    static void attach(View root) {
        if (root == null) return;
        Monitor monitor;
        synchronized (MONITORS) {
            monitor = MONITORS.get(root);
            if (monitor == null) {
                monitor = new Monitor(root);
                MONITORS.put(root, monitor);
            }
        }
        monitor.attach();
    }

    static void shutdownAll() {
        List<Monitor> snapshot;
        synchronized (MONITORS) {
            snapshot = new ArrayList<>(MONITORS.values());
            MONITORS.clear();
        }
        for (Monitor monitor : snapshot) {
            if (monitor != null) monitor.shutdown();
        }
    }

    private static final class Monitor implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<View> rootRef;
        boolean attachListenerAdded;
        boolean globalListenerAdded;
        Boolean lastVisible;
        boolean shutdown;

        Monitor(View root) {
            rootRef = new WeakReference<>(root);
        }

        void attach() {
            if (shutdown) return;
            View root = rootRef.get();
            if (root == null) return;
            if (!attachListenerAdded) {
                root.addOnAttachStateChangeListener(this);
                attachListenerAdded = true;
            }
            ensureGlobalListener(root);
            evaluate(root);
        }

        void shutdown() {
            if (shutdown) return;
            shutdown = true;
            View root = rootRef.get();
            if (root != null) {
                if (attachListenerAdded) root.removeOnAttachStateChangeListener(this);
                removeGlobalListener(root);
            }
            attachListenerAdded = false;
            lastVisible = null;
        }

        @Override public void onViewAttachedToWindow(View view) {
            if (shutdown) return;
            ensureGlobalListener(view);
            evaluate(view);
        }

        @Override public void onViewDetachedFromWindow(View view) {
            removeGlobalListener(view);
            transition(false, "root-detached", view);
        }

        @Override public void onGlobalLayout() {
            View root = rootRef.get();
            if (root != null && !shutdown) evaluate(root);
        }

        private void ensureGlobalListener(View root) {
            if (globalListenerAdded) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(this);
            globalListenerAdded = true;
        }

        private void removeGlobalListener(View root) {
            if (!globalListenerAdded || root == null) return;
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            } catch (Throwable t) {
                RateLimitedLog.error("nav-visibility-listener",
                        "failed to detach Topway nav visibility observer", t);
            } finally {
                globalListenerAdded = false;
            }
        }

        private void evaluate(View root) {
            View host = null;
            try {
                Resources resources = root.getResources();
                int id = resources.getIdentifier("navbar_left", "id", SYSTEM_UI_PACKAGE);
                if (id != 0) host = root.findViewById(id);
            } catch (Throwable t) {
                RateLimitedLog.error("nav-visibility-host",
                        "failed to resolve stock navbar_left visibility", t);
            }

            NavVisibilityPolicy.Decision decision = NavVisibilityPolicy.evaluate(
                    root.isAttachedToWindow(),
                    root.getVisibility() == View.VISIBLE,
                    root.getWindowVisibility() == View.VISIBLE,
                    root.isShown(),
                    host != null,
                    host != null && host.getVisibility() == View.VISIBLE,
                    host != null && host.isShown());
            transition(decision.visible, decision.reason, root);
        }

        private void transition(boolean visible, String reason, View root) {
            if (lastVisible != null && lastVisible == visible) return;
            lastVisible = visible;
            if (visible) {
                RateLimitedLog.always(
                        "stock Topway nav visible; rerunning exact topology/measurement preflight");
                ExactTopwayNavController.onInflated(root);
            } else {
                RateLimitedLog.always(
                        "right-nav suspended with stock panel: " + reason);
                ExactTopwayNavController.failOpen();
            }
        }
    }
}
