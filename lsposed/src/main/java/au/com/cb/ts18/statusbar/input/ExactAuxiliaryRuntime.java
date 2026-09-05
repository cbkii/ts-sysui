package au.com.cb.ts18.statusbar.input;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/** Starts exact read-only auxiliary observers only after SystemUI identity is proven. */
final class ExactAuxiliaryRuntime {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private ExactAuxiliaryRuntime() {}

    static void start(Context source) {
        if (source == null || !ExactSystemUiIdentity.isSupported()
                || !STARTED.compareAndSet(false, true)) return;

        startSafely("xtservice-observer", () -> ExactXtServiceObserver.start(source));
        startSafely("stock-nav-config-observer", () -> StockNavConfigObserver.start(source));
        startSafely("brightness-chronology", () -> BrightnessEventDiagnostics.start(source));
        startSafely("xtservice-diagnostic-bridge", () -> XtServiceDiagnosticBridge.install(source));

        DiagnosticJournal.state("auxiliary-runtime", "STARTED",
                "independent XTService vehicle observer + stock nav config observation"
                        + (BuildConfig.TS18_DIAGNOSTIC ? " + diagnostic qualification/brightness chronology" : ""));
    }

    private static void startSafely(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            DiagnosticJournal.failure("auxiliary-runtime-" + name,
                    "auxiliary start failed without blocking sibling observers", t);
        }
    }
}
