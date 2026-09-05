package au.com.cb.ts18.statusbar.input;

import java.util.concurrent.atomic.AtomicInteger;

/** Independent fail-open breaker for XTService observation/qualification only. */
final class XtServiceFeatureRuntime {
    private static final int MAX_FAILURES = 3;
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean breakerOpen;

    private XtServiceFeatureRuntime() {}

    static boolean isOperational() { return !breakerOpen; }
    static boolean isBreakerOpen() { return breakerOpen; }
    static int failureCount() { return FAILURES.get(); }

    static void recordFailure(String stage, Throwable error) {
        int count = FAILURES.incrementAndGet();
        RateLimitedLog.error("xtservice-" + stage,
                "XTService observer failure " + count + "/" + MAX_FAILURES, error);
        if (count < MAX_FAILURES || breakerOpen) return;
        synchronized (XtServiceFeatureRuntime.class) {
            if (breakerOpen) return;
            breakerOpen = true;
            DiagnosticJournal.state("xtservice-breaker", "OPEN",
                    "failures=" + count + " stage=" + stage);
            ExactXtServiceObserver.stopForProcess();
        }
    }
}
