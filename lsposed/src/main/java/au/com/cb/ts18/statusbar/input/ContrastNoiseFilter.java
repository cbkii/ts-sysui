package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Optional, process-local filter. No framework replacement or DropBox deletion. */
final class ContrastNoiseFilter {
    static final String KEY = "ts18_systemui_filter_contrast_wtf";
    private static volatile boolean enabled;
    private static boolean observing;

    private ContrastNoiseFilter() {}

    static boolean install(HookRegistry registry) {
        try {
            registry.addRequired("targeted contrast WTF", XposedHelpers.findAndHookMethod(
                    Log.class, "wtf", String.class, String.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (enabled && HookRuntime.isOperational() && ExactSystemUiIdentity.isSupported()
                                    && param.args != null && param.args.length == 2
                                    && ContrastNoisePolicy.matches((String) param.args[0], (String) param.args[1])) {
                                // Avoid the WTF handler/DropBox path, with no log per suppressed call.
                                param.setResult(0);
                            }
                        }
                    }));
            return true;
        } catch (Throwable t) {
            RateLimitedLog.error("contrast-noise-hook", "filter unavailable; stock logging retained", t);
            return false;
        }
    }

    static void start(Context context) {
        if (observing) return;
        try {
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { refresh(context); }
            };
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
            observing = true;
            refresh(context);
        } catch (Throwable t) {
            enabled = false;
            RateLimitedLog.error("contrast-noise-config", "observer unavailable; stock logging retained", t);
        }
    }

    private static void refresh(Context context) {
        try {
            enabled = "1".equals(Settings.Global.getString(context.getContentResolver(), KEY));
            DiagnosticJournal.state("contrast-noise-filter", enabled ? "ARMED" : "DISABLED",
                    "exact identity and operational runtime additionally required");
        } catch (Throwable t) {
            enabled = false;
            RateLimitedLog.error("contrast-noise-config", "read failed; stock logging retained", t);
        }
    }
}
