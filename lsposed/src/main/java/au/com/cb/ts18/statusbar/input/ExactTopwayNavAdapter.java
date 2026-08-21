package au.com.cb.ts18.statusbar.input;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Exact lifecycle hook for the decoded TS18 NavigationBarView contract. */
final class ExactTopwayNavAdapter {
    private ExactTopwayNavAdapter() {}

    static boolean installSafely(ClassLoader classLoader, HookRegistry registry) {
        try {
            Class<?> navigationBarView = Class.forName(
                    "com.android.systemui.statusbar.phone.NavigationBarView",
                    false, classLoader);
            navigationBarView.getDeclaredMethod("onFinishInflate");
            XC_MethodHook.Unhook hook = XposedHelpers.findAndHookMethod(
                    navigationBarView, "onFinishInflate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null
                                    || !(param.thisObject instanceof View)) return;
                            ExactTopwayNavController.onInflated((View) param.thisObject);
                        }
                    });
            registry.addRequired("exact Topway NavigationBarView.onFinishInflate", hook);
            RateLimitedLog.always(
                    "exact TS18 right-nav lifecycle installed; mutation awaits identity and policy gates");
            return true;
        } catch (Throwable t) {
            RateLimitedLog.error("exact-nav-contract",
                    "exact navbar contract mismatch; no navbar mutation installed", t);
            return false;
        }
    }

    static void failOpen() {
        ExactTopwayNavController.failOpen();
    }
}
