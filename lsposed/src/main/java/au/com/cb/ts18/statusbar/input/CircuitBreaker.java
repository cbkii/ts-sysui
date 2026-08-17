package au.com.cb.ts18.statusbar.input;

import java.util.concurrent.atomic.AtomicInteger;

final class CircuitBreaker {
    private static final int MAX_FAILURES = 3;
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean disabledForProcess;

    private CircuitBreaker() {}

    static boolean isOpen() { return disabledForProcess; }

    static void recordFailure(String stage, Throwable throwable) {
        int count = FAILURES.incrementAndGet();
        RateLimitedLog.error(stage + " failure " + count + "/" + MAX_FAILURES, throwable);
        if (count >= MAX_FAILURES) {
            disabledForProcess = true;
            RateLimitedLog.always("Circuit breaker opened; all TS18 status-bar hooks are fail-open until SystemUI restarts.");
        }
    }
}
