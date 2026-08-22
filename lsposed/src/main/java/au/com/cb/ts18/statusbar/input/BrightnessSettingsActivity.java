package au.com.cb.ts18.statusbar.input;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** API29 platform-only dashboard; no service or second scheduler is introduced. */
public final class BrightnessSettingsActivity extends Activity {
    private static final String[] MODE_LABELS = {
            "Auto (stock)", "Day", "Night", "Set auto (scheduled)"
    };
    private static final String[] NAV_ORDER_LABELS = {
            "Previous · Play/Pause · Next",
            "Previous · Next · Play/Pause",
            "Play/Pause · Previous · Next",
            "Play/Pause · Next · Previous",
            "Next · Previous · Play/Pause",
            "Next · Play/Pause · Previous"
    };
    private static final String[] NAV_ORDER_VALUES = {
            "previous,play_pause,next",
            "previous,next,play_pause",
            "play_pause,previous,next",
            "play_pause,next,previous",
            "next,previous,play_pause",
            "next,play_pause,previous"
    };
    private static final long BRIDGE_RESULT_TIMEOUT_MS = 3000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String pendingNonce;
    private Bundle lastStatus;
    private BrightnessPolicy.Config brightnessBeforeTest;

    private TextView bridgeStatus;
    private TextView overallStatus;
    private TextView diagnostics;
    private TextView compactFractionValue;
    private TextView navRuntimeStatus;
    private TextView brightnessRuntimeStatus;
    private TextView detectedBrightness;

    private Switch compactEnabled;
    private SeekBar compactFraction;

    private Switch navEnabled;
    private CheckBox navPrevious;
    private CheckBox navPlayPause;
    private CheckBox navNext;
    private Spinner navOrder;

    private Switch brightnessEnabled;
    private Spinner brightnessMode;
    private CheckBox manageDay;
    private SeekBar dayLevel;
    private TextView dayLevelValue;
    private CheckBox manageNight;
    private SeekBar nightLevel;
    private TextView nightLevelValue;
    private Button dayStart;
    private Button nightStart;
    private CheckBox brightnessDebug;
    private Button restoreBrightnessTest;
    private int dayStartMinute = 7 * 60;
    private int nightStartMinute = 19 * 60;

    private final ResultReceiver bridgeResultReceiver = new ResultReceiver(mainHandler) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            String nonce = data == null ? null : data.getString(BrightnessConfig.EXTRA_NONCE);
            if (pendingNonce == null || !pendingNonce.equals(nonce)) return;
            pendingNonce = null;
            mainHandler.removeCallbacks(bridgeTimeout);
            boolean success = data != null
                    && data.getBoolean(SystemUiBridge.EXTRA_SUCCESS, false)
                    && resultCode == BrightnessConfig.RESULT_APPLIED;
            String detail = data == null ? null : data.getString(SystemUiBridge.EXTRA_DETAIL);
            if (data != null) {
                lastStatus = new Bundle(data);
                renderStatus(lastStatus);
            }
            bridgeStatus.setText(success
                    ? "SystemUI bridge: " + safe(detail, "request completed")
                    : "SystemUI bridge: " + safe(detail, "request rejected"));
            if (!success) toast(safe(detail, "SystemUI request failed."));

            String response = data == null ? null
                    : data.getString(SystemUiBridge.EXTRA_RESPONSE_TYPE);
            if (SystemUiBridge.RESPONSE_APPLY.equals(response)) {
                // Configuration acknowledgement is not hardware confirmation.
                mainHandler.postDelayed(thisActivity()::queryStatus, 1800L);
            }
        }
    };

    private final Runnable bridgeTimeout = () -> {
        if (pendingNonce == null) return;
        pendingNonce = null;
        bridgeStatus.setText("SystemUI bridge: no reply. Check LSPosed scope/module load; no success is assumed.");
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("TS18 System UI");
        setContentView(buildUi());
        loadLocalConfig();
        queryStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (overallStatus != null) queryStatus();
    }

    @Override protected void onStop() {
        mainHandler.removeCallbacks(bridgeTimeout);
        pendingNonce = null;
        super.onStop();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(text("TS18 System UI", 26f));
        root.addView(text("Exact-device controls and live diagnostics. LSPosed scope must remain only com.android.systemui.", 15f));
        bridgeStatus = text("SystemUI bridge: checking…", 14f);
        overallStatus = text("Runtime status: checking…", 14f);
        root.addView(bridgeStatus);
        root.addView(overallStatus);
        Button refresh = button("Refresh live status");
        refresh.setOnClickListener(v -> queryStatus());
        root.addView(refresh);

        section(root, "Compact status bar");
        compactEnabled = new Switch(this);
        compactEnabled.setText("Enable compact collapsed-shade touch routing");
        touchHeight(compactEnabled);
        root.addView(compactEnabled);
        compactFractionValue = label("");
        root.addView(compactFractionValue);
        compactFraction = new SeekBar(this);
        compactFraction.setMax(19);
        compactFraction.setMinimumHeight(dp(56));
        compactFraction.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCompactFractionLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(compactFraction);
        root.addView(text("Hard safety: no more than 20% width and at least 64px from either top corner.", 13f));
        Button applyCompact = button("Apply compact touch settings");
        applyCompact.setOnClickListener(v -> applyCompact());
        root.addView(applyCompact);

        section(root, "Right sidebar media controls");
        navRuntimeStatus = text("Sidebar runtime: checking…", 14f);
        root.addView(navRuntimeStatus);
        navEnabled = new Switch(this);
        navEnabled.setText("Enable custom media controls");
        touchHeight(navEnabled);
        root.addView(navEnabled);
        navPrevious = check("Previous");
        navPlayPause = check("Play / Pause");
        navNext = check("Next");
        root.addView(navPrevious);
        root.addView(navPlayPause);
        root.addView(navNext);
        root.addView(label("Order"));
        navOrder = spinner(NAV_ORDER_LABELS);
        root.addView(navOrder);
        root.addView(text("Controls stay visible but disabled when no usable MediaController exists. Existing Home/Back/Recents/Volume controls are never replaced.", 13f));
        Button applyNav = button("Apply sidebar controls");
        applyNav.setOnClickListener(v -> applyNav());
        root.addView(applyNav);

        section(root, "Topway brightness");
        brightnessRuntimeStatus = text("Brightness runtime: checking…", 14f);
        detectedBrightness = text("Detected Topway state: checking…", 14f);
        root.addView(brightnessRuntimeStatus);
        root.addView(detectedBrightness);
        brightnessEnabled = new Switch(this);
        brightnessEnabled.setText("Enable brightness controller");
        touchHeight(brightnessEnabled);
        root.addView(brightnessEnabled);
        root.addView(label("Mode"));
        brightnessMode = spinner(MODE_LABELS);
        root.addView(brightnessMode);

        manageDay = check("Set day brightness level");
        root.addView(manageDay);
        dayLevelValue = label("");
        root.addView(dayLevelValue);
        dayLevel = levelSeekBar();
        root.addView(dayLevel);
        manageNight = check("Set night brightness level");
        root.addView(manageNight);
        nightLevelValue = label("");
        root.addView(nightLevelValue);
        nightLevel = levelSeekBar();
        root.addView(nightLevel);

        root.addView(label("Scheduled Day/Night transition times"));
        dayStart = button("");
        root.addView(dayStart);
        nightStart = button("");
        root.addView(nightStart);
        root.addView(text("Managed level 0 remains blocked. Day/Night mode changes can look identical when the stored slots are equal.", 13f));
        brightnessDebug = check("Debug logging");
        root.addView(brightnessDebug);

        Button applyBrightness = button("Save / apply brightness policy");
        applyBrightness.setOnClickListener(v -> applyBrightness(configFromUi(
                brightnessEnabled.isChecked())));
        root.addView(applyBrightness);
        Button testDay = button("Test Day (requires managed Day level)");
        testDay.setOnClickListener(v -> testBrightness(true));
        root.addView(testDay);
        Button testNight = button("Test Night (requires managed Night level)");
        testNight.setOnClickListener(v -> testBrightness(false));
        root.addView(testNight);
        restoreBrightnessTest = button("Restore policy from before last test");
        restoreBrightnessTest.setEnabled(false);
        restoreBrightnessTest.setOnClickListener(v -> restoreBrightnessTest());
        root.addView(restoreBrightnessTest);
        Button disableBrightness = button("Disable brightness controller");
        disableBrightness.setOnClickListener(v -> applyBrightness(configFromUi(false)));
        root.addView(disableBrightness);

        dayLevel.setOnSeekBarChangeListener(levelListener(dayLevelValue, "Day"));
        nightLevel.setOnSeekBarChangeListener(levelListener(nightLevelValue, "Night"));
        manageDay.setOnCheckedChangeListener((button, checked) -> dayLevel.setEnabled(checked));
        manageNight.setOnCheckedChangeListener((button, checked) -> nightLevel.setEnabled(checked));
        brightnessMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) { updateScheduleEnabled(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        dayStart.setOnClickListener(v -> pickTime(true));
        nightStart.setOnClickListener(v -> pickTime(false));

        section(root, "Diagnostics");
        diagnostics = text("No live status yet.", 13f);
        diagnostics.setTextIsSelectable(true);
        root.addView(diagnostics);
        Button copy = button("Copy diagnostics");
        copy.setOnClickListener(v -> copyDiagnostics());
        root.addView(copy);
        Button save = button("Save diagnostics to Downloads");
        save.setOnClickListener(v -> saveDiagnostics());
        root.addView(save);

        return scroll;
    }

    private void loadLocalConfig() {
        Config.invalidate();
        Config.Snapshot compact = Config.get(this);
        compactEnabled.setChecked(compact.enabled && compact.inputEnabled);
        int percent = Math.max(1, Math.min(20, Math.round(compact.touchFraction * 100f)));
        compactFraction.setProgress(percent - 1);
        updateCompactFractionLabel();

        NavConfig.invalidate();
        NavConfig.Snapshot nav = NavConfig.get(this);
        navEnabled.setChecked(nav.enabled);
        String navActions = actionIds(nav.actions);
        navPrevious.setChecked(navActions.contains("previous"));
        navPlayPause.setChecked(navActions.contains("play_pause"));
        navNext.setChecked(navActions.contains("next"));
        selectOrder(navActions);

        BrightnessPolicy.Config cfg = BrightnessConfig.read(this);
        brightnessEnabled.setChecked(cfg.enabled);
        brightnessMode.setSelection(modeIndex(cfg.mode));
        manageDay.setChecked(cfg.dayLevel != BrightnessPolicy.PRESERVE_LEVEL);
        dayLevel.setProgress((cfg.dayLevel == BrightnessPolicy.PRESERVE_LEVEL ? 10 : cfg.dayLevel) - 1);
        dayLevel.setEnabled(manageDay.isChecked());
        manageNight.setChecked(cfg.nightLevel != BrightnessPolicy.PRESERVE_LEVEL);
        nightLevel.setProgress((cfg.nightLevel == BrightnessPolicy.PRESERVE_LEVEL ? 6 : cfg.nightLevel) - 1);
        nightLevel.setEnabled(manageNight.isChecked());
        dayStartMinute = cfg.dayStartMinute;
        nightStartMinute = cfg.nightStartMinute;
        brightnessDebug.setChecked(cfg.debug);
        refreshTimeButtons();
        updateLevelLabel(dayLevelValue, "Day", dayLevel.getProgress() + 1);
        updateLevelLabel(nightLevelValue, "Night", nightLevel.getProgress() + 1);
        updateScheduleEnabled();
    }

    private void queryStatus() {
        if (pendingNonce != null) return;
        String nonce = UUID.randomUUID().toString();
        Intent request = baseBridgeIntent(SystemUiBridge.ACTION_QUERY_STATUS, nonce);
        pendingNonce = nonce;
        bridgeStatus.setText("SystemUI bridge: requesting live status…");
        sendBroadcast(request);
        mainHandler.removeCallbacks(bridgeTimeout);
        mainHandler.postDelayed(bridgeTimeout, BRIDGE_RESULT_TIMEOUT_MS);
    }

    private void applyCompact() {
        Intent request = new Intent();
        request.putExtra(SystemUiBridge.EXTRA_SECTION, SystemUiBridge.SECTION_COMPACT)
                .putExtra(BrightnessConfig.EXTRA_ENABLED, compactEnabled.isChecked())
                .putExtra(SystemUiBridge.EXTRA_COMPACT_INPUT, compactEnabled.isChecked())
                .putExtra(SystemUiBridge.EXTRA_COMPACT_FRACTION,
                        (compactFraction.getProgress() + 1) / 100f)
                .putExtra(SystemUiBridge.EXTRA_COMPACT_CORNER_GAP, 64)
                .putExtra(BrightnessConfig.EXTRA_DEBUG, false);
        sendApply(request);
    }

    private void applyNav() {
        String actions = selectedNavActions();
        if (navEnabled.isChecked() && "none".equals(actions)) {
            toast("Enable at least one media action, or disable custom sidebar controls.");
            return;
        }
        Intent request = new Intent();
        request.putExtra(SystemUiBridge.EXTRA_SECTION, SystemUiBridge.SECTION_NAV)
                .putExtra(BrightnessConfig.EXTRA_ENABLED, navEnabled.isChecked())
                .putExtra(SystemUiBridge.EXTRA_NAV_PROBE, false)
                .putExtra(SystemUiBridge.EXTRA_NAV_ACTIONS, actions)
                .putExtra(SystemUiBridge.EXTRA_NAV_MIN_TOUCH_DP, 56)
                .putExtra(BrightnessConfig.EXTRA_DEBUG, true);
        sendApply(request);
    }

    private void applyBrightness(BrightnessPolicy.Config config) {
        if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
            toast("Day and Night transition times must differ.");
            return;
        }
        Intent request = new Intent();
        request.putExtra(SystemUiBridge.EXTRA_SECTION, SystemUiBridge.SECTION_BRIGHTNESS)
                .putExtra(BrightnessConfig.EXTRA_ENABLED, config.enabled)
                .putExtra(BrightnessConfig.EXTRA_MODE, config.mode.persisted)
                .putExtra(BrightnessConfig.EXTRA_DAY_LEVEL, config.dayLevel)
                .putExtra(BrightnessConfig.EXTRA_NIGHT_LEVEL, config.nightLevel)
                .putExtra(BrightnessConfig.EXTRA_DAY_START_MINUTE, config.dayStartMinute)
                .putExtra(BrightnessConfig.EXTRA_NIGHT_START_MINUTE, config.nightStartMinute)
                .putExtra(BrightnessConfig.EXTRA_DEBUG, config.debug);
        sendApply(request);
    }

    private void sendApply(Intent extras) {
        if (pendingNonce != null) {
            toast("A SystemUI request is already pending.");
            return;
        }
        String nonce = UUID.randomUUID().toString();
        Intent request = baseBridgeIntent(SystemUiBridge.ACTION_APPLY, nonce);
        if (extras.getExtras() != null) request.putExtras(extras.getExtras());
        pendingNonce = nonce;
        bridgeStatus.setText("SystemUI bridge: applying policy; hardware confirmation is separate…");
        sendBroadcast(request);
        mainHandler.removeCallbacks(bridgeTimeout);
        mainHandler.postDelayed(bridgeTimeout, BRIDGE_RESULT_TIMEOUT_MS);
    }

    private Intent baseBridgeIntent(String action, String nonce) {
        return new Intent(action)
                .setPackage(BrightnessConfig.SYSTEMUI_PACKAGE)
                .putExtra(BrightnessConfig.EXTRA_NONCE, nonce)
                .putExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER, bridgeResultReceiver);
    }

    private void testBrightness(boolean day) {
        if (day && !manageDay.isChecked()) {
            toast("Enable and choose a safe Day level before testing Day.");
            return;
        }
        if (!day && !manageNight.isChecked()) {
            toast("Enable and choose a safe Night level before testing Night.");
            return;
        }
        brightnessBeforeTest = BrightnessConfig.read(this);
        restoreBrightnessTest.setEnabled(true);
        BrightnessPolicy.Config test = new BrightnessPolicy.Config(true,
                day ? BrightnessPolicy.ControlMode.DAY : BrightnessPolicy.ControlMode.NIGHT,
                manageDay.isChecked() ? dayLevel.getProgress() + 1 : BrightnessPolicy.PRESERVE_LEVEL,
                manageNight.isChecked() ? nightLevel.getProgress() + 1 : BrightnessPolicy.PRESERVE_LEVEL,
                dayStartMinute, nightStartMinute, true);
        applyBrightness(test);
    }

    private void restoreBrightnessTest() {
        BrightnessPolicy.Config previous = brightnessBeforeTest;
        if (previous == null) {
            restoreBrightnessTest.setEnabled(false);
            return;
        }
        applyBrightness(previous);
        brightnessBeforeTest = null;
        restoreBrightnessTest.setEnabled(false);
    }

    private void renderStatus(Bundle status) {
        if (status == null) return;
        boolean identity = status.getBoolean("exact_identity_supported", false);
        boolean bridge = status.getBoolean("bridge_ready", false);
        String identityState = status.getString("identity_state", "unknown");
        String identityDetail = status.getString("identity_detail", "");
        overallStatus.setText("Runtime: " + (identity && bridge ? "READY" : "BLOCKED")
                + " · identity=" + identityState + " · " + identityDetail
                + "\nRRO payloads: geometry=" + yesNo(status.getBoolean("geometry_overlay_mounted"))
                + ", visuals=" + yesNo(status.getBoolean("visual_overlay_mounted")));

        String navReason = status.getString("nav_preflight_reason", "unknown");
        navRuntimeStatus.setText("Sidebar: enabled=" + yesNo(status.getBoolean("nav_enabled"))
                + " · hook=" + yesNo(status.getBoolean("nav_hook_installed"))
                + " · root=" + yesNo(status.getBoolean("nav_root_seen"))
                + " · host=" + yesNo(status.getBoolean("nav_host_seen"))
                + "\npreflight=" + navReason
                + "\nhost=" + status.getInt("nav_host_width_px") + "×"
                + status.getInt("nav_host_height_px") + "px density="
                + formatFloat(status.getFloat("nav_density"))
                + " projectedCell=" + status.getInt("nav_projected_cell_px") + "px"
                + "\nmedia controllers=" + status.getInt("nav_media_controller_count")
                + " selected=" + safe(status.getString("nav_media_selected_package"), "none"));

        int detectedDay = status.getInt("brightness_detected_day_level", -1);
        int detectedNight = status.getInt("brightness_detected_night_level", -1);
        String runtime = status.getString("brightness_runtime_state", "unknown");
        String confirm = status.getString("brightness_confirmation", "unknown");
        brightnessRuntimeStatus.setText("Brightness: " + runtime
                + " · hooks=" + status.getInt("brightness_hook_count")
                + " · transport=" + yesNo(status.getBoolean("brightness_transport_ready"))
                + "\nconfirmation=" + confirm);
        StringBuilder detected = new StringBuilder("Detected Topway: mode=")
                .append(status.getInt("brightness_topway_mode", -1))
                .append(" day=").append(detectedDay < 0 ? "unknown" : detectedDay)
                .append(" night=").append(detectedNight < 0 ? "unknown" : detectedNight)
                .append(" effectiveNight=")
                .append(yesNo(status.getBoolean("brightness_effective_night")));
        if (detectedDay >= 0 && detectedNight >= 0 && detectedDay == detectedNight) {
            detected.append("\n⚠ Day and Night are both ").append(detectedDay)
                    .append("; changing mode alone will not visibly change brightness.");
        }
        detectedBrightness.setText(detected.toString());
        diagnostics.setText(formatDiagnostics(status));
    }

    private String formatDiagnostics(Bundle s) {
        if (s == null) return "No live status received.";
        StringBuilder out = new StringBuilder();
        line(out, "identity", s.getString("identity_state") + " / " + s.getString("identity_detail"));
        line(out, "bridge", yesNo(s.getBoolean("bridge_ready")) + " / " + s.getString("bridge_install_detail"));
        line(out, "geometry-overlay-mounted", yesNo(s.getBoolean("geometry_overlay_mounted")));
        line(out, "visual-overlay-mounted", yesNo(s.getBoolean("visual_overlay_mounted")));
        line(out, "compact", "enabled=" + s.getBoolean("compact_enabled")
                + " input=" + s.getBoolean("compact_input_enabled")
                + " fraction=" + s.getFloat("compact_fraction")
                + " gap=" + s.getInt("compact_corner_gap_px"));
        line(out, "nav", "enabled=" + s.getBoolean("nav_enabled")
                + " hook=" + s.getBoolean("nav_hook_installed")
                + " root=" + s.getBoolean("nav_root_seen")
                + " host=" + s.getBoolean("nav_host_seen")
                + " reason=" + s.getString("nav_preflight_reason"));
        line(out, "nav-host", s.getInt("nav_host_width_px") + "x"
                + s.getInt("nav_host_height_px") + " density=" + s.getFloat("nav_density")
                + " projected=" + s.getInt("nav_projected_cell_px")
                + " hFloor=" + s.getInt("nav_horizontal_floor_px")
                + " hPreferred=" + s.getInt("nav_horizontal_preferred_px")
                + " preferredMet=" + s.getBoolean("nav_horizontal_preferred_met"));
        line(out, "nav-stock", s.getString("nav_stock_summary"));
        line(out, "nav-injected", s.getString("nav_injected_actions"));
        line(out, "nav-media", "controllers=" + s.getInt("nav_media_controller_count")
                + " selected=" + s.getString("nav_media_selected_package")
                + " state=" + s.getInt("nav_media_playback_state")
                + " actions=0x" + Long.toHexString(s.getLong("nav_media_action_bits")));
        line(out, "nav-breaker", "open=" + s.getBoolean("nav_breaker_open")
                + " failures=" + s.getInt("nav_failure_count"));
        line(out, "brightness", "runtime=" + s.getString("brightness_runtime_state")
                + " compatible=" + s.getBoolean("brightness_compatible")
                + " transport=" + s.getBoolean("brightness_transport_ready")
                + " modeKnown=" + s.getBoolean("brightness_mode_known")
                + " levelsKnown=" + s.getBoolean("brightness_levels_known"));
        line(out, "brightness-state", "mode=" + s.getInt("brightness_topway_mode")
                + " day=" + s.getInt("brightness_detected_day_level")
                + " night=" + s.getInt("brightness_detected_night_level")
                + " effectiveNight=" + s.getBoolean("brightness_effective_night"));
        line(out, "brightness-callbacks", "258=" + s.getLong("brightness_last_258_callback_at")
                + " 516=" + s.getLong("brightness_last_516_callback_at"));
        line(out, "brightness-stock-write", s.getString("brightness_last_stock_write"));
        line(out, "brightness-module-action", s.getString("brightness_last_module_action"));
        line(out, "brightness-pending", s.getString("brightness_pending_action")
                + " attempts=" + s.getInt("brightness_pending_attempts"));
        line(out, "brightness-confirmation", s.getString("brightness_confirmation"));
        line(out, "brightness-breaker", "open=" + s.getBoolean("brightness_breaker_open")
                + " failures=" + s.getInt("brightness_failure_count"));
        return out.toString();
    }

    private void copyDiagnostics() {
        String value = diagnostics.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Clipboard unavailable.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("TS18 System UI diagnostics", value));
        toast("Diagnostics copied.");
    }

    private void saveDiagnostics() {
        String value = diagnostics.getText().toString();
        if (value.trim().isEmpty()) {
            toast("No diagnostics to save.");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ts18-sysui-" + stamp + ".txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TS18-SystemUI");
        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("output stream unavailable");
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            toast("Diagnostics saved to Downloads/TS18-SystemUI.");
        } catch (Throwable t) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); }
                catch (Throwable ignored) { }
            }
            toast("Could not save diagnostics: " + t.getClass().getSimpleName());
        }
    }

    private String selectedNavActions() {
        String[] preferred = NAV_ORDER_VALUES[Math.max(0,
                Math.min(NAV_ORDER_VALUES.length - 1, navOrder.getSelectedItemPosition()))]
                .split(",");
        StringBuilder out = new StringBuilder();
        for (String action : preferred) {
            boolean selected = "previous".equals(action) ? navPrevious.isChecked()
                    : "play_pause".equals(action) ? navPlayPause.isChecked()
                    : navNext.isChecked();
            if (selected) {
                if (out.length() > 0) out.append(',');
                out.append(action);
            }
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private void selectOrder(String actions) {
        if (actions == null || actions.isEmpty() || "none".equals(actions)) {
            navOrder.setSelection(0);
            return;
        }
        String[] tokens = actions.split(",");
        for (int i = 0; i < NAV_ORDER_VALUES.length; i++) {
            String candidate = NAV_ORDER_VALUES[i];
            int position = -1;
            boolean ordered = true;
            for (String token : tokens) {
                int next = candidate.indexOf(token);
                if (next < position) { ordered = false; break; }
                position = next;
            }
            if (ordered) { navOrder.setSelection(i); return; }
        }
        navOrder.setSelection(0);
    }

    private String actionIds(java.util.List<NavAction> actions) {
        if (actions == null || actions.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (NavAction action : actions) {
            if (out.length() > 0) out.append(',');
            out.append(action.id());
        }
        return out.toString();
    }

    private BrightnessPolicy.Config configFromUi(boolean enable) {
        return new BrightnessPolicy.Config(enable, selectedMode(),
                manageDay.isChecked() ? dayLevel.getProgress() + 1
                        : BrightnessPolicy.PRESERVE_LEVEL,
                manageNight.isChecked() ? nightLevel.getProgress() + 1
                        : BrightnessPolicy.PRESERVE_LEVEL,
                dayStartMinute, nightStartMinute, brightnessDebug.isChecked());
    }

    private void pickTime(boolean day) {
        int current = day ? dayStartMinute : nightStartMinute;
        new TimePickerDialog(this, (view, hour, minute) -> {
            int value = hour * 60 + minute;
            if (day) dayStartMinute = value; else nightStartMinute = value;
            refreshTimeButtons();
        }, current / 60, current % 60, true).show();
    }

    private void refreshTimeButtons() {
        dayStart.setText("Day starts: " + formatMinute(dayStartMinute));
        nightStart.setText("Night starts: " + formatMinute(nightStartMinute));
    }

    private void updateScheduleEnabled() {
        boolean scheduled = selectedMode() == BrightnessPolicy.ControlMode.SET_AUTO;
        dayStart.setEnabled(scheduled);
        nightStart.setEnabled(scheduled);
    }

    private BrightnessPolicy.ControlMode selectedMode() {
        switch (brightnessMode.getSelectedItemPosition()) {
            case 1: return BrightnessPolicy.ControlMode.DAY;
            case 2: return BrightnessPolicy.ControlMode.NIGHT;
            case 3: return BrightnessPolicy.ControlMode.SET_AUTO;
            default: return BrightnessPolicy.ControlMode.AUTO;
        }
    }

    private int modeIndex(BrightnessPolicy.ControlMode value) {
        switch (value) {
            case DAY: return 1;
            case NIGHT: return 2;
            case SET_AUTO: return 3;
            default: return 0;
        }
    }

    private SeekBar levelSeekBar() {
        SeekBar bar = new SeekBar(this);
        bar.setMax(BrightnessPolicy.MAX_LEVEL - BrightnessPolicy.MIN_LEVEL);
        bar.setProgress(5);
        bar.setMinimumHeight(dp(56));
        return bar;
    }

    private SeekBar.OnSeekBarChangeListener levelListener(TextView view, String label) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLevelLabel(view, label, progress + 1);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void updateCompactFractionLabel() {
        compactFractionValue.setText("Shade trigger width: "
                + (compactFraction.getProgress() + 1) + "% of physical width");
    }

    private void updateLevelLabel(TextView view, String label, int value) {
        view.setText(label + " level: " + value + "/10");
    }

    private void section(LinearLayout root, String title) {
        TextView view = text(title, 20f);
        view.setPadding(0, dp(20), 0, dp(8));
        root.addView(view);
    }

    private CheckBox check(String text) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        touchHeight(box);
        return box;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        touchHeight(spinner);
        return spinner;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        touchHeight(button);
        return button;
    }

    private TextView label(String value) { return text(value, 16f); }

    private TextView text(String value, float sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private void touchHeight(View view) { view.setMinimumHeight(dp(56)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String formatMinute(int minute) {
        int safe = BrightnessPolicy.clampMinute(minute, 0);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }

    private static String yesNo(boolean value) { return value ? "yes" : "no"; }
    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append('=').append(value == null ? "" : value).append('\n');
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private BrightnessSettingsActivity thisActivity() { return this; }
}
