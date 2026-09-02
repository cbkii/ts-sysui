package au.com.cb.ts18.statusbar.input;

/** Independent breaker: brightness failures never trip compact/nav runtime breakers. */
final class BrightnessFeatureRuntime {
    private static final int FAILURE_LIMIT = 3;
    private static int failures;
    private static volatile boolean compatible;
    private static volatile boolean disabledForProcess;

    private BrightnessFeatureRuntime() {}

    static boolean isOperational() { return compatible && !disabledForProcess; }
    static boolean isCompatible() { return compatible; }
    static boolean isBreakerOpen() { return disabledForProcess; }
    static synchronized int failureCount() { return failures; }

    static synchronized void markCompatible() {
        compatible = true;
        failures = 0;
        disabledForProcess = false;
        DiagnosticJournal.state("brightness-runtime", "READY",
                "compatible; breaker closed");
        RateLimitedLog.event("brightness-runtime",
                "compatibility verified; breaker closed");
    }

    static void markIncompatible(String reason) {
        compatible = false;
        DiagnosticJournal.state("brightness-runtime", "BLOCKED", reason);
        RateLimitedLog.event("brightness-runtime",
                "mutation disabled for this SystemUI: " + reason);
    }

    static synchronized void recordFailure(String stage, Throwable error) {
        failures++;
        RateLimitedLog.error("brightness-" + stage,
                "failure " + failures + "/" + FAILURE_LIMIT, error);
        if (failures >= FAILURE_LIMIT) {
            disabledForProcess = true;
            DiagnosticJournal.state("brightness-runtime", "BREAKER_OPEN",
                    "failures=" + failures);
            RateLimitedLog.event("brightness-runtime",
                    "process-local circuit breaker OPEN; stock behaviour retained until SystemUI restart");
            BrightnessController.stopMutationForProcess();
        }
    }
}
