package au.com.cb.ts18.statusbar.input;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Ts18StatusBarModule implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;
        if (lpparam.processName != null && !TARGET.equals(lpparam.processName)) return;

        try {
            // Install the observer hook first. Without a captured status-bar root it is inert,
            // so a later WindowManager-hook failure remains fail-open rather than partial.
            TouchableInsetsHook.install(lpparam.classLoader);
            WindowHooks.install(lpparam.classLoader);
            XposedBridge.log("TS18StatusBar: hooks installed in " + lpparam.processName
                    + "; fail-open circuit breaker active");
        } catch (Throwable t) {
            // Installation failure means no mutation, rather than a partially guessed global hook.
            XposedBridge.log("TS18StatusBar: hook installation failed open: " + t);
        }
    }
}
