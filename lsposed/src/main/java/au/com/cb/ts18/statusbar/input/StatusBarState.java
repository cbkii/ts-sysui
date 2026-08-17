package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

final class StatusBarState {
    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static volatile int windowHeightSpec = Integer.MIN_VALUE;

    private StatusBarState() {}

    static synchronized View capture(View root, ViewGroup.LayoutParams params) {
        View previous = rootRef.get();
        rootRef = new WeakReference<>(root);
        updateParams(params);
        return previous;
    }

    static synchronized void clear() {
        rootRef = new WeakReference<>(null);
        windowHeightSpec = Integer.MIN_VALUE;
    }

    static void updateIfTracked(View view, ViewGroup.LayoutParams params) {
        View root = root();
        if (root != null && root == view) updateParams(params);
    }

    private static void updateParams(ViewGroup.LayoutParams params) {
        if (params != null) windowHeightSpec = params.height;
    }

    static View root() {
        return rootRef.get();
    }

    static int windowHeightSpec() {
        return windowHeightSpec;
    }

    static boolean isStatusBarParams(Object value) {
        if (!(value instanceof WindowManager.LayoutParams)) return false;
        return ((WindowManager.LayoutParams) value).type == WindowManager.LayoutParams.TYPE_STATUS_BAR;
    }
}
