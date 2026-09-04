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
        ExactXtServiceObserver.start(source);
        StockNavConfigObserver.start(source);
        BrightnessEventDiagnostics.start(source);
        XtServiceDiagnosticBridge.install(source);
        DiagnosticJournal.state("auxiliary-runtime", "STARTED",
                "XTService vehicle observer + stock nav config observation"
                        + (BuildConfig.TS18_DIAGNOSTIC ? " + diagnostic qualification/brightness chronology" : ""));
    }
}
