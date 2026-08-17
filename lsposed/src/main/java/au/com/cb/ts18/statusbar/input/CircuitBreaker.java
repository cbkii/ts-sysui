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

            VisualScaler.RollbackResult rollback = VisualScaler.RollbackResult.empty();
            try {
                rollback = VisualScaler.failOpen();
            } catch (Throwable rollbackFailure) {
                RateLimitedLog.error("breaker-visual-rollback",
                        "visual fail-open cleanup threw; continuing breaker cleanup",
                        rollbackFailure);
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
                        + "; further mutations are disabled until SystemUI restarts; "
                        + "visual rollback restored=" + rollback.restored
                        + " released=" + rollback.releasedWithoutWrite
                        + " listeners=" + rollback.listenersRemoved + ".");
            } catch (Throwable ignored) {
                // The breaker must remain non-throwing even if logging itself fails.
            }
        }
    }
}
