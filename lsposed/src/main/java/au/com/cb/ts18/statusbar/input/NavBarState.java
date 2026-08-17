package au.com.cb.ts18.statusbar.input;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

final class NavBarState {
    // TYPE_NAVIGATION_BAR is @hide in the Android SDK stubs. Android 10 framework
    // defines it as FIRST_SYSTEM_WINDOW (2000) + 19. Keep the API29 value local
    // instead of reflecting across hidden-API enforcement from SystemUI.
    private static final int TYPE_NAVIGATION_BAR_API29 = 2019;

    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static int generation;
    private static volatile int widthSpec = Integer.MIN_VALUE;
    private static volatile int heightSpec = Integer.MIN_VALUE;

    private NavBarState() {}

    static synchronized Capture capture(View root, ViewGroup.LayoutParams params) {
        View previous = rootRef.get();
        if (previous != root) generation++;
        rootRef = new WeakReference<>(root);
        updateParams(params);
        return new Capture(previous, generation);
    }

    static synchronized void clear() {
        rootRef = new WeakReference<>(null);
        widthSpec = Integer.MIN_VALUE;
        heightSpec = Integer.MIN_VALUE;
        generation++;
    }

    static void updateIfTracked(View view, ViewGroup.LayoutParams params) {
        View root = root();
        if (root != null && root == view) updateParams(params);
    }

    private static void updateParams(ViewGroup.LayoutParams params) {
        if (params == null) return;
        widthSpec = params.width;
        heightSpec = params.height;
    }

    static View root() {
        return rootRef.get();
    }

    static synchronized int generation() {
        return generation;
    }

    static int widthSpec() {
        return widthSpec;
    }

    static int heightSpec() {
        return heightSpec;
    }

    static boolean isNavigationBarParams(Object value) {
        if (!(value instanceof WindowManager.LayoutParams)) return false;
        return ((WindowManager.LayoutParams) value).type == TYPE_NAVIGATION_BAR_API29;
    }

    static final class Capture {
        final View previous;
        final int generation;

        Capture(View previous, int generation) {
            this.previous = previous;
            this.generation = generation;
        }
    }
}
