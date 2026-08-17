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
        if (!HookRuntime.beginInstall()) return;

        HookRegistry registry = new HookRegistry();
        try {
            TouchableInsetsHook.install(lpparam.classLoader, registry);
            WindowHooks.install(lpparam.classLoader, registry);
            HookRuntime.activate();
            XposedBridge.log("TS18StatusBar: " + registry.size()
                    + " hook registrations installed in " + lpparam.processName
                    + "; runtime mutations are inert until explicitly armed; circuit breaker active");
        } catch (Throwable t) {
            HookRuntime.deactivate();
            registry.unhookAll();
            XposedBridge.log("TS18StatusBar: hook installation failed open; partial hooks removed: " + t);
            XposedBridge.log(t);
        }
    }
}
