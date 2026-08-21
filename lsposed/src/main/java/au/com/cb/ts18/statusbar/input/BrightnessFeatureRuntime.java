package au.com.cb.ts18.statusbar.input;

import de.robv.android.xposed.XposedBridge;

/** Independent breaker: brightness failures never trip compact/nav runtime breakers. */
final class BrightnessFeatureRuntime {
    private static final int FAILURE_LIMIT = 3;
    private static int failures;
    private static volatile boolean compatible;
    private static volatile boolean disabledForProcess;

    private BrightnessFeatureRuntime() {}

    static boolean isOperational() { return compatible && !disabledForProcess; }

    static void markCompatible() {
        compatible = true;
        failures = 0;
        disabledForProcess = false;
    }

    static void markIncompatible(String reason) {
        compatible = false;
        XposedBridge.log("TS18Brightness: mutation disabled for this SystemUI: " + reason);
    }

    static synchronized void recordFailure(String stage, Throwable error) {
        failures++;
        XposedBridge.log("TS18Brightness: failure stage=" + stage + " count=" + failures
                + '/' + FAILURE_LIMIT + ": " + error);
        if (failures == 1) XposedBridge.log(error);
        if (failures >= FAILURE_LIMIT) {
            disabledForProcess = true;
            XposedBridge.log("TS18Brightness: process-local circuit breaker OPEN; stock behaviour retained until SystemUI restart");
            BrightnessController.stopMutationForProcess();
        }
    }
}
