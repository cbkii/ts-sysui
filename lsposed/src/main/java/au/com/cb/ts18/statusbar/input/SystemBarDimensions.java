package au.com.cb.ts18.statusbar.input;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.WindowInsets;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class SystemBarDimensions {
    private static final int UNRESOLVED = Integer.MIN_VALUE;
    private static final Map<Resources, Cache> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SystemBarDimensions() {}

    static int statusBarHeight(View root) {
        Resources res = root.getResources();
        Cache cache = cacheFor(res);
        synchronized (cache) {
            cache.refreshConfiguration(res);
            if (cache.statusBarHeightPx == UNRESOLVED) {
                cache.statusBarHeightPx = dimensionPixelSize(res, cache.statusBarHeightId);
            }
            return Math.max(0, cache.statusBarHeightPx);
        }
    }

    @SuppressWarnings("deprecation") // Android 10 API 29 runtime; newer inset APIs are not backported.
    static int rightSystemInset(View root) {
        WindowInsets wi = root.getRootWindowInsets();
        if (wi != null) {
            int inset = Math.max(wi.getStableInsetRight(), wi.getSystemWindowInsetRight());
            if (inset > 0) return inset;
        }

        Resources res = root.getResources();
        Cache cache = cacheFor(res);
        int width;
        synchronized (cache) {
            cache.refreshConfiguration(res);
            if (cache.navigationBarWidthPx == UNRESOLVED) {
                cache.navigationBarWidthPx = dimensionPixelSize(res, cache.navigationBarWidthId);
            }
            width = cache.navigationBarWidthPx;
        }
        return width > 0 && width < root.getWidth() / 4 ? width : 0;
    }

    static void clearCache() {
        CACHES.clear();
    }

    private static Cache cacheFor(Resources res) {
        Cache cache = CACHES.get(res);
        if (cache != null) return cache;
        synchronized (CACHES) {
            cache = CACHES.get(res);
            if (cache == null) {
                cache = new Cache(
                        res.getIdentifier("status_bar_height", "dimen", "android"),
                        res.getIdentifier("navigation_bar_width", "dimen", "android"));
                CACHES.put(res, cache);
            }
            return cache;
        }
    }

    private static int dimensionPixelSize(Resources res, int id) {
        if (id == 0) return 0;
        try {
            return res.getDimensionPixelSize(id);
        } catch (Resources.NotFoundException e) {
            return 0;
        }
    }

    private static final class Cache {
        final int statusBarHeightId;
        final int navigationBarWidthId;
        int densityDpi = UNRESOLVED;
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        int statusBarHeightPx = UNRESOLVED;
        int navigationBarWidthPx = UNRESOLVED;

        Cache(int statusBarHeightId, int navigationBarWidthId) {
            this.statusBarHeightId = statusBarHeightId;
            this.navigationBarWidthId = navigationBarWidthId;
        }

        void refreshConfiguration(Resources res) {
            int newDensity = res.getDisplayMetrics().densityDpi;
            int newOrientation = res.getConfiguration().orientation;
            if (newDensity == densityDpi && newOrientation == orientation) return;
            densityDpi = newDensity;
            orientation = newOrientation;
            statusBarHeightPx = UNRESOLVED;
            navigationBarWidthPx = UNRESOLVED;
        }
    }
}
