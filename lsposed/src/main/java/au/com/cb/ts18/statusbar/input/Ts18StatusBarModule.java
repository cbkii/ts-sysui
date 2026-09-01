package au.com.cb.ts18.statusbar.input;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Ts18StatusBarModule implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String process = lpparam == null ? null : lpparam.processName;
        String pkg = lpparam == null ? null : lpparam.packageName;
        DiagnosticJournal.state("module-entry", "ENTER",
                "package=" + safe(pkg) + " process=" + safe(process)
                        + " build=" + BuildConfig.TS18_BUILD_KIND
                        + " diagnostic=" + BuildConfig.TS18_DIAGNOSTIC);
        RateLimitedLog.event("module-entry",
                "handleLoadPackage package=" + safe(pkg) + " process=" + safe(process)
                        + " version=" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE
                        + " build=" + BuildConfig.TS18_BUILD_KIND);

        if (lpparam == null) {
            DiagnosticJournal.state("module-entry", "IGNORED", "null load-package parameter");
            return;
        }
        if (!TARGET.equals(lpparam.packageName)) {
            DiagnosticJournal.state("module-entry", "IGNORED",
                    "unexpected package=" + safe(lpparam.packageName));
            return;
        }
        if (lpparam.processName != null && !TARGET.equals(lpparam.processName)) {
            DiagnosticJournal.state("module-entry", "IGNORED",
                    "unexpected process=" + safe(lpparam.processName));
            return;
        }
        if (!HookRuntime.beginInstall()) {
            DiagnosticJournal.state("bootstrap", "SKIPPED",
                    "HookRuntime rejected duplicate/non-installable invocation");
            RateLimitedLog.event("bootstrap", "HookRuntime.beginInstall() returned false");
            return;
        }

        HookRegistry registry = new HookRegistry();
        DiagnosticJournal.state("bootstrap", "INSTALLING",
                "required hooks start; registry=0");
        try {
            installRequired("identity-hook",
                    () -> ExactSystemUiIdentity.install(lpparam.classLoader, registry));

            installBooleanFeature("exact-touch-hook",
                    ExactTs18TouchableRegionAdapter.installSafely(
                            lpparam.classLoader, registry));

            installBooleanFeature("exact-nav-hook",
                    ExactTopwayNavAdapter.installSafely(
                            lpparam.classLoader, registry));

            // These compatibility hooks remain part of the existing runtime
            // contract. Diagnostic builds identify them separately so a future
            // remediation can isolate an exact failure instead of guessing which
            // required hook caused the all-or-nothing rollback.
            installRequired("compat-touch-hook",
                    () -> TouchableInsetsHook.install(lpparam.classLoader, registry));
            installRequired("window-hooks",
                    () -> WindowHooks.install(lpparam.classLoader, registry));

            HookRuntime.activate();
            DiagnosticJournal.state("hook-runtime", "ACTIVE",
                    "required registrations=" + registry.size());
            RateLimitedLog.event("hook-runtime",
                    "required hook runtime activated registrations=" + registry.size());

            DiagnosticJournal.state("brightness-hooks", "INSTALLING",
                    "optional brightness observation hooks");
            int brightnessHooks = BrightnessHooks.installSafely(lpparam.classLoader);
            DiagnosticJournal.state("brightness-hooks",
                    brightnessHooks > 0 ? "INSTALLED" : "UNAVAILABLE",
                    "count=" + brightnessHooks);

            DiagnosticJournal.state("bootstrap", "ACTIVE",
                    "required=" + registry.size() + " brightness=" + brightnessHooks);
            RateLimitedLog.event("bootstrap",
                    registry.size() + " required hook registrations installed in "
                            + safe(lpparam.processName)
                            + "; brightness observation hooks=" + brightnessHooks
                            + "; mutations remain policy-gated; independent circuit breakers active");
        } catch (Throwable t) {
            DiagnosticJournal.failure("bootstrap", "required hook installation failed", t);
            DiagnosticJournal.state("bootstrap", "ROLLED_BACK",
                    "required hook failed=" + t.getClass().getSimpleName()
                            + "; registrations-before-rollback=" + registry.size());
            HookRuntime.deactivate();
            registry.unhookAll();
            RateLimitedLog.error("bootstrap",
                    "required hook installation failed open; partial hooks removed", t);
        }
    }

    private static void installRequired(String stage, InstallAction action) throws Throwable {
        DiagnosticJournal.state(stage, "INSTALLING", "");
        RateLimitedLog.event(stage, "install begin");
        try {
            action.run();
            DiagnosticJournal.state(stage, "INSTALLED", "");
            RateLimitedLog.event(stage, "install success");
        } catch (Throwable t) {
            DiagnosticJournal.failure(stage, "install failed", t);
            DiagnosticJournal.state(stage, "FAILED", t.getClass().getSimpleName());
            RateLimitedLog.error(stage, "install failed", t);
            throw t;
        }
    }

    private static void installBooleanFeature(String stage, boolean installed) {
        DiagnosticJournal.state(stage, installed ? "INSTALLED" : "UNAVAILABLE",
                installed ? "adapter registered" : "adapter failed open");
        RateLimitedLog.event(stage,
                installed ? "adapter install success" : "adapter unavailable; feature remains stock");
    }

    private static String safe(String value) {
        return value == null ? "null"
                : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
    }

    private interface InstallAction {
        void run() throws Throwable;
    }
}
