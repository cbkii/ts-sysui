package au.com.cb.ts18.statusbar.input;

import java.util.concurrent.atomic.AtomicBoolean;

final class HookRuntime {
    private static final AtomicBoolean INSTALL_ATTEMPTED = new AtomicBoolean();
    private static volatile boolean active;

    private HookRuntime() {}

    static boolean beginInstall() { return INSTALL_ATTEMPTED.compareAndSet(false, true); }
    static void activate() { active = true; }
    static void deactivate() { active = false; }
    static boolean isOperational() { return active && !CircuitBreaker.isOpen(); }
}
