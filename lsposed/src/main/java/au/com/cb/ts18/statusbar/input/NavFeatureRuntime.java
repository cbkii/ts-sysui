package au.com.cb.ts18.statusbar.input;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicInteger;

final class NavFeatureRuntime {
    private static final int MAX_FAILURES = 3;
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean disabledForProcess;

    private NavFeatureRuntime() {}

    static boolean isOperational() {
        return !disabledForProcess;
    }

    static boolean isBreakerOpen() {
        return disabledForProcess;
    }

    static int failureCount() {
        return FAILURES.get();
    }

    static void recordFailure(String stage, Throwable throwable) {
        int count = FAILURES.incrementAndGet();
        RateLimitedLog.error("nav-" + stage,
                "right-nav failure " + count + "/" + MAX_FAILURES, throwable);
        if (count < MAX_FAILURES || disabledForProcess) return;

        synchronized (NavFeatureRuntime.class) {
            if (disabledForProcess) return;
            disabledForProcess = true;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                cleanupOnMain();
            } else {
                new Handler(Looper.getMainLooper()).post(NavFeatureRuntime::cleanupOnMain);
            }
        }
    }

    private static void cleanupOnMain() {
        try {
            ExactTopwayNavAdapter.failOpen();
        } catch (Throwable cleanupFailure) {
            RateLimitedLog.error("nav-breaker-controller",
                    "right-nav controller cleanup failed; feature remains disabled",
                    cleanupFailure);
        }
        try {
            NavHierarchyProbe.failOpen();
        } catch (Throwable cleanupFailure) {
            RateLimitedLog.error("nav-breaker-cleanup",
                    "right-nav probe cleanup failed; feature remains disabled",
                    cleanupFailure);
        }
        try {
            NavBarState.clear();
        } catch (Throwable stateFailure) {
            RateLimitedLog.error("nav-breaker-state",
                    "right-nav state clear failed; feature remains disabled",
                    stateFailure);
        }
        try {
            RateLimitedLog.always(
                    "right-nav circuit breaker opened; nav observation/mutation is disabled "
                            + "until SystemUI restarts; compact status-bar runtime remains active");
        } catch (Throwable ignored) {
            // This feature breaker must never propagate into the compact status-bar path.
        }
    }
}
