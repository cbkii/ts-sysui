package au.com.cb.ts18.statusbar.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import java.util.concurrent.atomic.AtomicBoolean;

/** Signature-protected diagnostic-only bridge for auxiliary exact-device qualification. */
final class XtServiceDiagnosticBridge {
    static final String ACTION_QUERY = BrightnessConfig.MODULE_PACKAGE
            + ".action.QUERY_AUXILIARY_STATUS";
    static final String ACTION_QUALIFY_MEDIA = BrightnessConfig.MODULE_PACKAGE
            + ".action.QUALIFY_XTSERVICE_MEDIA";
    static final String EXTRA_MEDIA_ACTION = "xtservice_media_action";
    static final String EXTRA_SUCCESS = "aux_success";
    static final String EXTRA_DETAIL = "aux_detail";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static BroadcastReceiver receiver;

    private XtServiceDiagnosticBridge() {}

    static void install(Context source) {
        if (!BuildConfig.TS18_DIAGNOSTIC || source == null || REGISTERED.get()) return;
        Context app = source.getApplicationContext();
        Context finalApp = app == null ? source : app;
        MAIN.post(() -> installOnMain(finalApp));
    }

    private static synchronized void installOnMain(Context app) {
        if (REGISTERED.get()) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_QUERY);
            filter.addAction(ACTION_QUALIFY_MEDIA);
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    handle(receiverContext, intent);
                }
            };
            app.registerReceiver(receiver, filter, BrightnessConfig.CONFIGURE_PERMISSION, MAIN);
            REGISTERED.set(true);
            DiagnosticJournal.state("xtservice-diagnostic-bridge", "READY",
                    "diagnostic-only signature-protected receiver");
        } catch (Throwable t) {
            DiagnosticJournal.failure("xtservice-diagnostic-bridge",
                    "could not register diagnostic qualification bridge", t);
        }
    }

    private static void handle(Context receiverContext, Intent intent) {
        if (intent == null) return;
        ResultReceiver result = intent.getParcelableExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER);
        String nonce = intent.getStringExtra(BrightnessConfig.EXTRA_NONCE);
        if (result == null) return;
        String action = intent.getAction();
        if (ACTION_QUERY.equals(action)) {
            send(result, nonce, true, "status", buildStatus());
            return;
        }
        if (!ACTION_QUALIFY_MEDIA.equals(action)) {
            send(result, nonce, false, "unexpected auxiliary action", buildStatus());
            return;
        }
        if (!BuildConfig.TS18_DIAGNOSTIC || !ExactSystemUiIdentity.isSupported()) {
            send(result, nonce, false,
                    "qualification blocked: diagnostic build and exact SystemUI identity required",
                    buildStatus());
            return;
        }
        String mediaAction = intent.getStringExtra(EXTRA_MEDIA_ACTION);
        if (!ExactXtServiceBinder.isQualificationAction(mediaAction)) {
            send(result, nonce, false, "unsupported qualification action", buildStatus());
            return;
        }
        ExactXtServiceObserver.qualifyMedia(mediaAction, (success, detail) ->
                send(result, nonce, success, detail, buildStatus()));
    }

    private static Bundle buildStatus() {
        Bundle out = new Bundle();
        out.putBoolean("aux_bridge_ready", REGISTERED.get());
        out.putString("identity_state", ExactSystemUiIdentity.state().name());
        out.putString("identity_detail", ExactSystemUiIdentity.detail());
        ExactXtServiceObserver.appendStatus(out);
        StockNavConfigObserver.appendStatus(out);
        ExactTopwayNavController.appendStatus(out);
        BrightnessController.appendStatus(out);
        BrightnessEventDiagnostics.appendStatus(out);
        out.putBoolean("nav_breaker_open", NavFeatureRuntime.isBreakerOpen());
        out.putBoolean("brightness_breaker_open", BrightnessFeatureRuntime.isBreakerOpen());
        return out;
    }

    private static void send(ResultReceiver receiver, String nonce, boolean success,
                             String detail, Bundle status) {
        try {
            Bundle data = status == null ? new Bundle() : new Bundle(status);
            data.putString(BrightnessConfig.EXTRA_NONCE, nonce);
            data.putBoolean(EXTRA_SUCCESS, success);
            data.putString(EXTRA_DETAIL, detail);
            receiver.send(success ? 1 : 0, data);
        } catch (Throwable t) {
            RateLimitedLog.error("xtservice-diagnostic-result",
                    "could not return auxiliary diagnostic result", t);
        }
    }
}
