package au.com.cb.ts18.statusbar.input;

import android.content.res.Resources;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class NavHierarchyProbe {
    private static final int MAX_NODES = 96;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_TEXT_CHARS = 16000;
    private static final int MAX_LABEL_CHARS = 80;
    private static final long MIN_CAPTURE_INTERVAL_MS = 3000L;

    private static final Map<View, Binding> ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NavHierarchyProbe() {}

    static void sync(View root, int generation) {
        if (root == null || !NavFeatureRuntime.isOperational()) return;
        NavConfig.Snapshot config = NavConfig.get(root.getContext());
        if (!config.probeEnabled) {
            detach(root);
            return;
        }
        attach(root, generation);
    }

    static void attach(View root, int generation) {
        if (root == null || !NavFeatureRuntime.isOperational()) return;
        synchronized (ROOTS) {
            Binding existing = ROOTS.get(root);
            if (existing != null) {
                existing.generation = generation;
                root.post(() -> snapshot(root));
                return;
            }
            View.OnLayoutChangeListener listener =
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
                            -> snapshot(v);
            ROOTS.put(root, new Binding(listener, generation));
            root.addOnLayoutChangeListener(listener);
        }
        root.post(() -> snapshot(root));
    }

    static void detach(View root) {
        if (root == null) return;
        Binding binding;
        synchronized (ROOTS) {
            binding = ROOTS.remove(root);
        }
        if (binding == null) return;
        try {
            root.removeOnLayoutChangeListener(binding.listener);
        } catch (Throwable t) {
            RateLimitedLog.error("nav-probe-detach",
                    "failed to remove right-nav observation listener", t);
        }
    }

    static void failOpen() {
        List<Map.Entry<View, Binding>> entries;
        synchronized (ROOTS) {
            entries = new ArrayList<>(ROOTS.entrySet());
            ROOTS.clear();
        }
        for (Map.Entry<View, Binding> entry : entries) {
            View root = entry.getKey();
            if (root == null) continue;
            try {
                root.removeOnLayoutChangeListener(entry.getValue().listener);
            } catch (Throwable t) {
                RateLimitedLog.error("nav-probe-rollback",
                        "failed to detach one right-nav observation listener", t);
            }
        }
    }

    private static void snapshot(View root) {
        try {
            if (root == null || !NavFeatureRuntime.isOperational()) return;
            NavConfig.Snapshot config = NavConfig.get(root.getContext());
            if (!config.probeEnabled) {
                detach(root);
                return;
            }

            Binding binding;
            synchronized (ROOTS) {
                binding = ROOTS.get(root);
            }
            if (binding == null) return;

            long now = SystemClock.elapsedRealtime();
            synchronized (binding) {
                if (now - binding.lastCaptureAt < MIN_CAPTURE_INTERVAL_MS) return;
                binding.lastCaptureAt = now;
            }

            Snapshot snapshot = buildSnapshot(root, binding.generation, config.debug);
            synchronized (binding) {
                if (snapshot.fingerprint == binding.lastFingerprint) return;
                binding.lastFingerprint = snapshot.fingerprint;
            }

            RateLimitedLog.always(snapshot.text);
        } catch (Throwable t) {
            NavFeatureRuntime.recordFailure("probe", t);
        }
    }

    private static Snapshot buildSnapshot(View root, int generation, boolean includeLabels) {
        StringBuilder out = new StringBuilder(4096);
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);

        Point realSize = new Point(-1, -1);
        Display display = root.getDisplay();
        if (display != null) display.getRealSize(realSize);

        out.append("right-nav probe generation=").append(generation)
                .append(" root=").append(root.getClass().getName())
                .append(" screenBounds=[")
                .append(rootLocation[0]).append(',').append(rootLocation[1]).append(',')
                .append(root.getWidth()).append(',').append(root.getHeight()).append(']')
                .append(" display=").append(realSize.x).append('x').append(realSize.y)
                .append(" density=").append(root.getResources().getDisplayMetrics().density)
                .append(" layoutSpec=").append(NavBarState.widthSpec())
                .append('x').append(NavBarState.heightSpec())
                .append('\n');

        Counter counter = new Counter();
        appendNode(root, rootLocation, 0, "0", out, counter, includeLabels);
        if (counter.truncated) {
            out.append("... probe truncated at ")
                    .append(counter.count).append(" nodes / ")
                    .append(MAX_TEXT_CHARS).append(" chars\n");
        }
        return new Snapshot(counter.fingerprint, out.toString());
    }

    private static void appendNode(View view, int[] rootLocation, int depth, String path,
                                   StringBuilder out, Counter counter, boolean includeLabels) {
        if (view == null || counter.truncated) return;
        if (counter.count >= MAX_NODES || depth > MAX_DEPTH || out.length() >= MAX_TEXT_CHARS) {
            counter.truncated = true;
            return;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int relX = location[0] - rootLocation[0];
        int relY = location[1] - rootLocation[1];

        String resourceName = resourceName(view);
        String description = includeLabels ? bounded(view.getContentDescription()) : "";
        ViewGroup.LayoutParams params = view.getLayoutParams();

        counter.count++;
        counter.mix(view.getClass().getName().hashCode());
        counter.mix(view.getId());
        counter.mix(relX);
        counter.mix(relY);
        counter.mix(view.getWidth());
        counter.mix(view.getHeight());
        counter.mix(view.getVisibility());
        counter.mix(view.isClickable() ? 1 : 0);
        counter.mix(view.isLongClickable() ? 1 : 0);
        counter.mix(view.isEnabled() ? 1 : 0);
        counter.mix(view.isFocusable() ? 1 : 0);

        indent(out, depth);
        out.append(path)
                .append(" class=").append(view.getClass().getName())
                .append(" id=").append(resourceName)
                .append(" rootBounds=[")
                .append(relX).append(',').append(relY).append(',')
                .append(view.getWidth()).append(',').append(view.getHeight()).append(']')
                .append(" screen=[")
                .append(location[0]).append(',').append(location[1]).append(']')
                .append(" vis=").append(view.getVisibility())
                .append(" clickable=").append(view.isClickable())
                .append(" long=").append(view.isLongClickable())
                .append(" enabled=").append(view.isEnabled())
                .append(" focusable=").append(view.isFocusable())
                .append(" lp=").append(params == null ? "null" : params.getClass().getName());
        if (!description.isEmpty()) out.append(" desc=").append(description);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            out.append(" children=").append(group.getChildCount());
            counter.mix(group.getChildCount());
            out.append('\n');
            for (int i = 0; i < group.getChildCount(); i++) {
                appendNode(group.getChildAt(i), rootLocation, depth + 1,
                        path + "/" + i, out, counter, includeLabels);
                if (counter.truncated) return;
            }
        } else {
            out.append('\n');
        }
    }

    private static String resourceName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "NO_ID";
        try {
            return view.getResources().getResourceName(id);
        } catch (Resources.NotFoundException e) {
            return "0x" + Integer.toHexString(id);
        }
    }

    private static String bounded(CharSequence value) {
        if (value == null) return "";
        String text = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() <= MAX_LABEL_CHARS) return text;
        return text.substring(0, MAX_LABEL_CHARS) + "…";
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("  ");
    }

    private static final class Binding {
        final View.OnLayoutChangeListener listener;
        volatile int generation;
        long lastCaptureAt = -MIN_CAPTURE_INTERVAL_MS;
        int lastFingerprint = Integer.MIN_VALUE;

        Binding(View.OnLayoutChangeListener listener, int generation) {
            this.listener = listener;
            this.generation = generation;
        }
    }

    private static final class Snapshot {
        final int fingerprint;
        final String text;

        Snapshot(int fingerprint, String text) {
            this.fingerprint = fingerprint;
            this.text = text;
        }
    }

    private static final class Counter {
        int count;
        int fingerprint = 17;
        boolean truncated;

        void mix(int value) {
            fingerprint = 31 * fingerprint + value;
        }
    }
}
