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

            VisualScaler.RollbackResult rollback = VisualScaler.failOpen();
            StatusBarState.clear();
            RateLimitedLog.always("Circuit breaker opened at stage=" + stage
                    + "; further mutations are disabled until SystemUI restarts; "
                    + "visual rollback restored=" + rollback.restored
                    + " released=" + rollback.releasedWithoutWrite
                    + " listeners=" + rollback.listenersRemoved + ".");
        }
    }
}
