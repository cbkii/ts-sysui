package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class VisualScaler {
    private static final Map<View, Scale> originals = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> attachedRoots = Collections.synchronizedMap(new WeakHashMap<>());

    private VisualScaler() {}

    static void attach(View root) {
        if (attachedRoots.put(root, Boolean.TRUE) != null) return;
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> apply(root));
        root.post(() -> apply(root));
    }

    private static void apply(View root) {
        try {
            if (CircuitBreaker.isOpen()) return;
            Config.Snapshot cfg = Config.get(root.getContext());
            if (!cfg.enabled || !cfg.visualEnabled) {
                restoreTree(root);
                return;
            }
            int barHeight = SystemBarDimensions.statusBarHeight(root);
            if (barHeight <= 0) return;
            scaleLeaves(root, root, barHeight, cfg.visualScale);
        } catch (Throwable t) {
            CircuitBreaker.recordFailure("visual scale", t);
        }
    }

    private static void scaleLeaves(View root, View node, int barHeight, float factor) {
        if (node.getVisibility() != View.VISIBLE) return;
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleLeaves(root, group.getChildAt(i), barHeight, factor);
            }
            return;
        }

        int[] rootLoc = new int[2];
        int[] viewLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        node.getLocationOnScreen(viewLoc);
        int top = viewLoc[1] - rootLoc[1];
        int bottom = top + node.getHeight();
        if (top >= barHeight || bottom <= 0) return;

        Scale original = originals.get(node);
        if (original == null) {
            original = new Scale(node.getScaleX(), node.getScaleY());
            originals.put(node, original);
        }
        node.setScaleX(original.x * factor);
        node.setScaleY(original.y * factor);
    }

    private static void restoreTree(View node) {
        Scale original = originals.remove(node);
        if (original != null) {
            node.setScaleX(original.x);
            node.setScaleY(original.y);
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) restoreTree(group.getChildAt(i));
        }
    }

    private static final class Scale {
        final float x;
        final float y;
        Scale(float x, float y) { this.x = x; this.y = y; }
    }
}
