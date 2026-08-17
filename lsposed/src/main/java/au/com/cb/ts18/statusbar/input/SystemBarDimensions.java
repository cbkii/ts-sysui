package au.com.cb.ts18.statusbar.input;

import android.content.res.Resources;
import android.view.View;
import android.view.WindowInsets;

final class SystemBarDimensions {
    private SystemBarDimensions() {}

    static int statusBarHeight(View root) {
        Resources res = root.getResources();
        int id = res.getIdentifier("status_bar_height", "dimen", "android");
        if (id == 0) return 0;
        try { return res.getDimensionPixelSize(id); }
        catch (Resources.NotFoundException e) { return 0; }
    }

    static int rightSystemInset(View root) {
        int inset = 0;
        WindowInsets wi = root.getRootWindowInsets();
        if (wi != null) {
            inset = Math.max(wi.getStableInsetRight(), wi.getSystemWindowInsetRight());
        }
        if (inset > 0) return inset;

        // TS18 fallback: the right navigation bar is a framework dimension.
        Resources res = root.getResources();
        int id = res.getIdentifier("navigation_bar_width", "dimen", "android");
        if (id != 0) {
            try {
                int width = res.getDimensionPixelSize(id);
                if (width > 0 && width < root.getWidth() / 4) return width;
            } catch (Resources.NotFoundException ignored) {
                // Expected safe fallback: no right inset.
            }
        }
        return 0;
    }
}
