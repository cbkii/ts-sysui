package au.com.cb.ts18.statusbar.input;

import java.util.concurrent.atomic.AtomicInteger;

final class CircuitBreaker {
    private static final int MAX_FAILURES = 3;
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean disabledForProcess;

    private CircuitBreaker() {}

    static boolean isOpen() {
        return disabledForProcess;
    }

    static void recordFailure(String stage, Throwable throwable) {
        int count = FAILURES.incrementAndGet();
        RateLimitedLog.error(stage, "failure " + count + "/" + MAX_FAILURES, throwable);
        if (count < MAX_FAILURES || disabledForProcess) return;

        synchronized (CircuitBreaker.class) {
            if (disabledForProcess) return;
            disabledForProcess = true;
            HookRuntime.deactivate();

            try {
                ExactTs18TouchableRegionAdapter.failOpen();
            } catch (Throwable touchFailure) {
                RateLimitedLog.error("breaker-exact-touch",
                        "exact touch fail-open cleanup threw; continuing breaker cleanup",
                        touchFailure);
            }
            try {
                StatusBarState.clear();
            } catch (Throwable stateFailure) {
                RateLimitedLog.error("breaker-state-clear",
                        "status-bar state cleanup threw; breaker remains open",
                        stateFailure);
            }
            try {
                RateLimitedLog.always("Circuit breaker opened at stage=" + stage
                        + "; further compact mutations are disabled until SystemUI restarts.");
            } catch (Throwable ignored) {
                // The breaker must remain non-throwing even if logging itself fails.
            }
        }
    }
}
