package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class VisualScaler {
    private static final float EPSILON = 0.0005f;
    private static final Map<View, OwnedScale> OWNED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> CONFLICTED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, RootBinding> ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<int[]> ROOT_LOCATION =
            ThreadLocal.withInitial(() -> new int[2]);
    private static final ThreadLocal<int[]> VIEW_LOCATION =
            ThreadLocal.withInitial(() -> new int[2]);

    private VisualScaler() {}

    static void sync(View root) {
        if (root == null) return;
        Config.Snapshot cfg = Config.get(root.getContext());
        if (cfg.enabled && cfg.visualEnabled) {
            attach(root);
            return;
        }
        // Observation-only mode should not walk the SystemUI tree on every
        // WindowManager update. Restore only if this root was actually armed.
        synchronized (ROOTS) {
            if (!ROOTS.containsKey(root)) return;
        }
        detach(root, true);
    }

    static void attach(View root) {
        if (root == null) return;
        synchronized (ROOTS) {
            if (ROOTS.containsKey(root)) return;
            View.OnLayoutChangeListener listener =
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> apply(v);
            ROOTS.put(root, new RootBinding(listener));
            root.addOnLayoutChangeListener(listener);
        }
        root.post(() -> apply(root));
    }

    static void detach(View root, boolean restore) {
        if (root == null) return;
        RootBinding binding;
        synchronized (ROOTS) {
            binding = ROOTS.remove(root);
        }
        if (binding != null) {
            try {
                root.removeOnLayoutChangeListener(binding.listener);
            } catch (Throwable t) {
                RateLimitedLog.error("visual-detach", "failed to remove layout listener", t);
            }
        }
        if (restore) restoreTree(root, true);
    }

    static RollbackResult failOpen() {
        int listenersRemoved = 0;
        List<Map.Entry<View, RootBinding>> roots;
        synchronized (ROOTS) {
            roots = new ArrayList<>(ROOTS.entrySet());
            ROOTS.clear();
        }
        for (Map.Entry<View, RootBinding> entry : roots) {
            View root = entry.getKey();
            if (root == null) continue;
            try {
                root.removeOnLayoutChangeListener(entry.getValue().listener);
                listenersRemoved++;
            } catch (Throwable t) {
                RateLimitedLog.error("visual-rollback-listener",
                        "failed to remove layout listener during fail-open", t);
            }
        }

        int restored = 0;
        int releasedWithoutWrite = 0;
        List<Map.Entry<View, OwnedScale>> owned;
        synchronized (OWNED) {
            owned = new ArrayList<>(OWNED.entrySet());
            OWNED.clear();
        }
        for (Map.Entry<View, OwnedScale> entry : owned) {
            View view = entry.getKey();
            if (view == null) continue;
            try {
                if (restoreOwnedAxes(view, entry.getValue())) restored++;
                else releasedWithoutWrite++;
            } catch (Throwable t) {
                releasedWithoutWrite++;
                RateLimitedLog.error("visual-rollback-view",
                        "failed to restore one owned visual during fail-open", t);
            }
        }
        try {
            CONFLICTED.clear();
        } catch (Throwable t) {
            RateLimitedLog.error("visual-rollback-conflicts",
                    "failed to clear visual conflict tracking", t);
        }
        return new RollbackResult(restored, releasedWithoutWrite, listenersRemoved);
    }

    private static void apply(View root) {
        try {
            if (!HookRuntime.isOperational()) return;
            Config.Snapshot cfg = Config.get(root.getContext());
            if (!cfg.enabled || !cfg.visualEnabled) {
                // Do not leave an observation-only listener traversing SystemUI forever.
                // A later explicit visual-on is applied on the next tracked window update
                // or, as documented, after a SystemUI restart/reboot.
                detach(root, true);
                return;
            }
            int barHeight = SystemBarDimensions.statusBarHeight(root);
            if (barHeight <= 0) {
                restoreTree(root, true);
                return;
            }

            int[] rootLocation = ROOT_LOCATION.get();
            root.getLocationOnScreen(rootLocation);
            int[] viewLocation = VIEW_LOCATION.get();
            scaleLeaves(root, rootLocation[1], viewLocation, barHeight, cfg.visualScale);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("visual-scale", t);
        }
    }

    private static void scaleLeaves(View node,
                                    int rootY,
                                    int[] viewLocation,
                                    int barHeight,
                                    float factor) {
        if (node.getVisibility() != View.VISIBLE) {
            restoreTree(node, false);
            return;
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleLeaves(group.getChildAt(i), rootY, viewLocation, barHeight, factor);
            }
            return;
        }

        node.getLocationOnScreen(viewLocation);
        int top = viewLocation[1] - rootY;
        int bottom = top + node.getHeight();
        boolean inTarget = top < barHeight && bottom > 0;

        OwnedScale owned = OWNED.get(node);
        boolean conflicted = CONFLICTED.containsKey(node);
        boolean matchesApplied = owned != null && matchesApplied(node, owned);
        VisualScalePolicy.Action action = VisualScalePolicy.decide(
                owned != null, conflicted, inTarget, matchesApplied);

        switch (action) {
            case CAPTURE_AND_APPLY:
                OwnedScale captured = new OwnedScale(node.getScaleX(), node.getScaleY());
                OWNED.put(node, captured);
                applyOwned(node, captured, factor);
                break;
            case APPLY_OWNED:
                applyOwned(node, owned, factor);
                break;
            case RESTORE_AND_RELEASE:
                OWNED.remove(node);
                restoreOwnedAxes(node, owned);
                break;
            case RELEASE_CONFLICT:
                // Restore any axis that still has the value this module applied before
                // releasing the leaf. If SystemUI/animation changed only one axis, the
                // untouched axis remains module-owned and must not be left scaled.
                OWNED.remove(node);
                restoreOwnedAxes(node, owned);
                CONFLICTED.put(node, Boolean.TRUE);
                break;
            case SKIP:
                break;
        }
    }

    private static void applyOwned(View view, OwnedScale owned, float factor) {
        float desiredX = owned.originalX * factor;
        float desiredY = owned.originalY * factor;
        if (!approximately(view.getScaleX(), desiredX)) view.setScaleX(desiredX);
        if (!approximately(view.getScaleY(), desiredY)) view.setScaleY(desiredY);
        owned.appliedX = desiredX;
        owned.appliedY = desiredY;
    }

    private static boolean matchesApplied(View view, OwnedScale owned) {
        return approximately(view.getScaleX(), owned.appliedX)
                && approximately(view.getScaleY(), owned.appliedY);
    }

    private static boolean restoreOwnedAxes(View view, OwnedScale owned) {
        if (owned == null) return false;
        boolean restoredAny = false;

        if (owned.ownsX) {
            float currentX = view.getScaleX();
            if (approximately(currentX, owned.appliedX)) {
                if (!approximately(currentX, owned.originalX)) view.setScaleX(owned.originalX);
                restoredAny = true;
            } else {
                owned.ownsX = false;
            }
        }

        if (owned.ownsY) {
            float currentY = view.getScaleY();
            if (approximately(currentY, owned.appliedY)) {
                if (!approximately(currentY, owned.originalY)) view.setScaleY(owned.originalY);
                restoredAny = true;
            } else {
                owned.ownsY = false;
            }
        }

        return restoredAny;
    }

    private static void restoreTree(View node, boolean clearConflicts) {
        OwnedScale owned = OWNED.remove(node);
        if (owned != null) {
            try {
                restoreOwnedAxes(node, owned);
            } catch (Throwable t) {
                RateLimitedLog.error("visual-restore-tree",
                        "failed to restore one owned visual", t);
            }
        }
        if (clearConflicts) CONFLICTED.remove(node);
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                restoreTree(group.getChildAt(i), clearConflicts);
            }
        }
    }

    private static boolean approximately(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    static final class RollbackResult {
        final int restored;
        final int releasedWithoutWrite;
        final int listenersRemoved;

        RollbackResult(int restored, int releasedWithoutWrite, int listenersRemoved) {
            this.restored = restored;
            this.releasedWithoutWrite = releasedWithoutWrite;
            this.listenersRemoved = listenersRemoved;
        }

        static RollbackResult empty() {
            return new RollbackResult(0, 0, 0);
        }
    }

    private static final class RootBinding {
        final View.OnLayoutChangeListener listener;
        RootBinding(View.OnLayoutChangeListener listener) { this.listener = listener; }
    }

    private static final class OwnedScale {
        final float originalX;
        final float originalY;
        float appliedX;
        float appliedY;
        boolean ownsX = true;
        boolean ownsY = true;

        OwnedScale(float originalX, float originalY) {
            this.originalX = originalX;
            this.originalY = originalY;
            this.appliedX = originalX;
            this.appliedY = originalY;
        }
    }
}
