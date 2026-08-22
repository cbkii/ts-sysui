package au.com.cb.ts18.statusbar.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Signature-protected, package-targeted control/status bridge injected into the
 * already-privileged exact SystemUI process. It owns no service and grants the
 * normal module APK no Android signature permission.
 */
final class SystemUiBridge {
    static final String ACTION_QUERY_STATUS = BrightnessConfig.MODULE_PACKAGE
            + ".action.QUERY_SYSTEM_UI_STATUS";
    static final String ACTION_APPLY = BrightnessConfig.MODULE_PACKAGE
            + ".action.APPLY_SYSTEM_UI_CONFIG";

    static final String SECTION_COMPACT = "compact";
    static final String SECTION_NAV = "nav";
    static final String SECTION_BRIGHTNESS = "brightness";

    static final String EXTRA_SECTION = "section";
    static final String EXTRA_RESPONSE_TYPE = "response_type";
    static final String EXTRA_SUCCESS = "bridge_success";
    static final String EXTRA_DETAIL = "bridge_detail";
    static final String RESPONSE_STATUS = "status";
    static final String RESPONSE_APPLY = "apply";

    static final String EXTRA_NAV_ACTIONS = "nav_actions";
    static final String EXTRA_NAV_MIN_TOUCH_DP = "nav_min_touch_dp";
    static final String EXTRA_NAV_PROBE = "nav_probe";

    static final String EXTRA_COMPACT_INPUT = "compact_input";
    static final String EXTRA_COMPACT_FRACTION = "compact_fraction";
    static final String EXTRA_COMPACT_CORNER_GAP = "compact_corner_gap";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context context;
    private static BroadcastReceiver receiver;
    private static volatile String installDetail = "not-installed";

    private SystemUiBridge() {}

    static void install(Context source) {
        if (source == null || REGISTERED.get()) return;
        Context app = source.getApplicationContext();
        if (app == null) app = source;
        Context finalApp = app;
        MAIN.post(() -> installOnMain(finalApp));
    }

    private static synchronized void installOnMain(Context app) {
        if (REGISTERED.get()) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_QUERY_STATUS);
            filter.addAction(ACTION_APPLY);
            // Preserve the 0.5.1 brightness Activity request contract while the
            // dashboard migrates to the unified action.
            filter.addAction(BrightnessConfig.ACTION_APPLY);
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    handle(receiverContext, intent);
                }
            };
            app.registerReceiver(receiver, filter,
                    BrightnessConfig.CONFIGURE_PERMISSION, MAIN);
            context = app;
            installDetail = "ready";
            REGISTERED.set(true);
            RateLimitedLog.always("TS18 SystemUI control/status bridge registered");
        } catch (Throwable t) {
            installDetail = "register-" + t.getClass().getSimpleName();
            RateLimitedLog.error("systemui-bridge-register",
                    "TS18 SystemUI bridge registration failed", t);
        }
    }

    private static void handle(Context receiverContext, Intent intent) {
        if (intent == null) return;
        ResultReceiver result = resultReceiver(intent);
        String nonce = intent.getStringExtra(BrightnessConfig.EXTRA_NONCE);
        if (result == null) {
            RateLimitedLog.always("TS18 SystemUI bridge ignored request without private ResultReceiver");
            return;
        }
        try {
            String action = intent.getAction();
            if (ACTION_QUERY_STATUS.equals(action)) {
                send(result, nonce, true, RESPONSE_STATUS, "status", buildStatus(receiverContext));
                return;
            }
            if (!ExactSystemUiIdentity.isSupported()) {
                send(result, nonce, false, RESPONSE_APPLY,
                        "blocked: exact SystemUI identity is " + ExactSystemUiIdentity.state()
                                + " (" + ExactSystemUiIdentity.detail() + ")",
                        buildStatus(receiverContext));
                return;
            }

            if (BrightnessConfig.ACTION_APPLY.equals(action)) {
                applyBrightness(receiverContext, BrightnessConfig.fromRequest(intent));
                send(result, nonce, true, RESPONSE_APPLY,
                        "brightness policy saved; runtime confirmation is reported separately",
                        buildStatus(receiverContext));
                return;
            }
            if (!ACTION_APPLY.equals(action)) {
                throw new IllegalArgumentException("unexpected bridge action");
            }

            String section = intent.getStringExtra(EXTRA_SECTION);
            if (SECTION_NAV.equals(section)) {
                NavConfig.persistFromSystemUi(receiverContext,
                        intent.getBooleanExtra(BrightnessConfig.EXTRA_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_NAV_PROBE, false),
                        intent.getStringExtra(EXTRA_NAV_ACTIONS),
                        intent.getIntExtra(EXTRA_NAV_MIN_TOUCH_DP, NavConfig.DEFAULT_TOUCH_DP),
                        intent.getBooleanExtra(BrightnessConfig.EXTRA_DEBUG, false));
                ExactTopwayNavController.requestReconcile();
                send(result, nonce, true, RESPONSE_APPLY,
                        "right-nav policy consumed; reconciliation requested immediately",
                        buildStatus(receiverContext));
            } else if (SECTION_COMPACT.equals(section)) {
                boolean enabled = intent.getBooleanExtra(BrightnessConfig.EXTRA_ENABLED, false);
                Config.persistFromSystemUi(receiverContext,
                        enabled,
                        intent.getBooleanExtra(EXTRA_COMPACT_INPUT, enabled),
                        intent.getFloatExtra(EXTRA_COMPACT_FRACTION, 0.20f),
                        intent.getIntExtra(EXTRA_COMPACT_CORNER_GAP,
                                TouchStripGeometry.MIN_CORNER_GAP_PX),
                        intent.getBooleanExtra(BrightnessConfig.EXTRA_DEBUG, false));
                send(result, nonce, true, RESPONSE_APPLY,
                        "compact policy consumed; exact adapter will use it on the next stock inset computation",
                        buildStatus(receiverContext));
            } else if (SECTION_BRIGHTNESS.equals(section)) {
                applyBrightness(receiverContext, BrightnessConfig.fromValues(
                        intent.getBooleanExtra(BrightnessConfig.EXTRA_ENABLED, false),
                        intent.getStringExtra(BrightnessConfig.EXTRA_MODE),
                        intent.getIntExtra(BrightnessConfig.EXTRA_DAY_LEVEL, Integer.MIN_VALUE),
                        intent.getIntExtra(BrightnessConfig.EXTRA_NIGHT_LEVEL, Integer.MIN_VALUE),
                        intent.getIntExtra(BrightnessConfig.EXTRA_DAY_START_MINUTE, -1),
                        intent.getIntExtra(BrightnessConfig.EXTRA_NIGHT_START_MINUTE, -1),
                        intent.getBooleanExtra(BrightnessConfig.EXTRA_DEBUG, false)));
                send(result, nonce, true, RESPONSE_APPLY,
                        "brightness policy saved; hardware confirmation pending runtime 258/516 state",
                        buildStatus(receiverContext));
            } else {
                throw new IllegalArgumentException("unknown bridge section");
            }
        } catch (Throwable t) {
            RateLimitedLog.error("systemui-bridge-request",
                    "TS18 SystemUI bridge rejected request", t);
            send(result, nonce, false, RESPONSE_APPLY,
                    "request rejected: " + t.getClass().getSimpleName() + ": "
                            + safeMessage(t), buildStatus(receiverContext));
        }
    }

    private static void applyBrightness(Context receiverContext,
                                        BrightnessPolicy.Config config) {
        BrightnessConfig.persistFromSystemUi(receiverContext, config);
        BrightnessController.onConfigurationChanged();
    }

    static Bundle buildStatus(Context source) {
        Context app = source == null ? context : source;
        Bundle out = new Bundle();
        out.putBoolean("bridge_ready", REGISTERED.get());
        out.putString("bridge_install_detail", installDetail);
        out.putString("identity_state", ExactSystemUiIdentity.state().name());
        out.putString("identity_detail", ExactSystemUiIdentity.detail());
        out.putBoolean("exact_identity_supported", ExactSystemUiIdentity.isSupported());
        out.putBoolean("nav_hook_installed", ExactTopwayNavAdapter.isInstalled());
        out.putInt("brightness_hook_count", BrightnessHooks.installedCount());
        out.putBoolean("geometry_overlay_mounted",
                new File("/product/overlay/TS18StatusBarGeometry.apk").canRead());
        out.putBoolean("visual_overlay_mounted",
                new File("/product/overlay/TS18StatusBarVisuals.apk").canRead());

        if (app != null) {
            Config.Snapshot compact = Config.get(app);
            out.putBoolean("compact_enabled", compact.enabled);
            out.putBoolean("compact_input_enabled", compact.inputEnabled);
            out.putString("compact_adapter", compact.adapterMode.name());
            out.putFloat("compact_fraction", compact.touchFraction);
            out.putInt("compact_corner_gap_px", compact.cornerGapPx);

            NavConfig.Snapshot nav = NavConfig.get(app);
            out.putBoolean("nav_enabled", nav.enabled);
            out.putBoolean("nav_probe_enabled", nav.probeEnabled);
            out.putInt("nav_min_touch_dp", nav.minTouchDp);
            out.putString("nav_actions", navActionIds(nav.actions));

            BrightnessPolicy.Config brightness = BrightnessConfig.read(app);
            out.putBoolean("brightness_enabled", brightness.enabled);
            out.putString("brightness_mode", brightness.mode.persisted);
            out.putInt("brightness_config_day_level", brightness.dayLevel);
            out.putInt("brightness_config_night_level", brightness.nightLevel);
            out.putInt("brightness_day_start_minute", brightness.dayStartMinute);
            out.putInt("brightness_night_start_minute", brightness.nightStartMinute);
        }

        ExactTopwayNavController.appendStatus(out);
        BrightnessController.appendStatus(out);
        out.putBoolean("nav_breaker_open", NavFeatureRuntime.isBreakerOpen());
        out.putInt("nav_failure_count", NavFeatureRuntime.failureCount());
        out.putBoolean("brightness_breaker_open", BrightnessFeatureRuntime.isBreakerOpen());
        out.putInt("brightness_failure_count", BrightnessFeatureRuntime.failureCount());
        return out;
    }

    private static String navActionIds(java.util.List<NavAction> actions) {
        if (actions == null || actions.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (NavAction action : actions) {
            if (out.length() > 0) out.append(',');
            out.append(action.id());
        }
        return out.toString();
    }

    private static ResultReceiver resultReceiver(Intent intent) {
        return intent == null ? null
                : intent.getParcelableExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER);
    }

    private static void send(ResultReceiver receiver, String nonce, boolean success,
                             String responseType, String detail, Bundle status) {
        if (receiver == null) return;
        try {
            Bundle data = status == null ? new Bundle() : new Bundle(status);
            data.putString(BrightnessConfig.EXTRA_NONCE, nonce);
            data.putString(EXTRA_RESPONSE_TYPE, responseType);
            data.putBoolean(EXTRA_SUCCESS, success);
            data.putBoolean(BrightnessConfig.EXTRA_SUCCESS, success);
            data.putString(EXTRA_DETAIL, detail);
            data.putString(BrightnessConfig.EXTRA_DETAIL, detail);
            receiver.send(success ? BrightnessConfig.RESULT_APPLIED
                    : BrightnessConfig.RESULT_REJECTED, data);
        } catch (Throwable t) {
            RateLimitedLog.error("systemui-bridge-result",
                    "TS18 SystemUI bridge could not return private result", t);
        }
    }

    private static String safeMessage(Throwable t) {
        String value = t == null ? null : t.getMessage();
        return value == null || value.trim().isEmpty() ? "no detail" : value.trim();
    }
}
