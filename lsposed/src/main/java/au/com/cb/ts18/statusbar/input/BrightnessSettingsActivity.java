package au.com.cb.ts18.statusbar.input;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
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

import java.util.Locale;
import java.util.UUID;

/** Small API29 platform-only UI; no service or second scheduler is introduced. */
public final class BrightnessSettingsActivity extends Activity {
    private static final String[] MODE_LABELS = { "Auto (stock)", "Day", "Night", "Set auto (scheduled)" };
    private static final long CONFIG_RESULT_TIMEOUT_MS = 2500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Switch enabled;
    private Spinner mode;
    private CheckBox manageDay;
    private SeekBar dayLevel;
    private TextView dayLevelValue;
    private CheckBox manageNight;
    private SeekBar nightLevel;
    private TextView nightLevelValue;
    private Button dayStart;
    private Button nightStart;
    private CheckBox debug;
    private TextView bridgeStatus;
    private int dayStartMinute = 7 * 60;
    private int nightStartMinute = 19 * 60;
    private String pendingNonce;

    private final ResultReceiver configResultReceiver = new ResultReceiver(mainHandler) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            String nonce = data == null ? null : data.getString(BrightnessConfig.EXTRA_NONCE);
            if (pendingNonce == null || !pendingNonce.equals(nonce)) return;
            pendingNonce = null;
            mainHandler.removeCallbacks(configTimeout);
            boolean success = data != null
                    && data.getBoolean(BrightnessConfig.EXTRA_SUCCESS, false)
                    && resultCode == BrightnessConfig.RESULT_APPLIED;
            String detail = data == null ? null : data.getString(BrightnessConfig.EXTRA_DETAIL);
            bridgeStatus.setText(success
                    ? "Configuration bridge: applied by the exact-gated SystemUI process."
                    : "Configuration bridge: request rejected; stock behaviour retained.");
            if (success) load();
            toast(detail == null || detail.trim().isEmpty()
                    ? (success ? "Brightness policy applied." : "Brightness policy was not changed.")
                    : detail);
        }
    };

    private final Runnable configTimeout = () -> {
        if (pendingNonce == null) return;
        pendingNonce = null;
        bridgeStatus.setText("Configuration bridge: no compatible SystemUI reply. No success is assumed.");
        toast("No compatible SystemUI configuration bridge replied. Check LSPosed scope/logs or use the root fallback helper.");
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("TS18 Brightness");
        setContentView(buildUi());
        load();
    }

    @Override protected void onStop() {
        mainHandler.removeCallbacks(configTimeout);
        pendingNonce = null;
        super.onStop();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(text("TS18 Brightness Controller", 24f));
        root.addView(text("Uses the exact Topway 0–10 command/callback path. Set auto switches explicit Day/Night modes at your chosen local times instead of relying on stock Auto/ILL.", 16f));
        bridgeStatus = text("Configuration bridge: requires the LSPosed SystemUI scope and exact supported SystemUI binary.", 14f);
        bridgeStatus.setPadding(0, dp(12), 0, dp(12));
        root.addView(bridgeStatus);
        enabled = new Switch(this);
        enabled.setText("Enable brightness controller");
        touchHeight(enabled);
        root.addView(enabled);
        root.addView(label("Mode"));
        mode = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MODE_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(adapter);
        touchHeight(mode);
        root.addView(mode);
        manageDay = new CheckBox(this);
        manageDay.setText("Set day brightness level");
        touchHeight(manageDay);
        root.addView(manageDay);
        dayLevelValue = label("");
        root.addView(dayLevelValue);
        dayLevel = levelSeekBar();
        root.addView(dayLevel);
        manageNight = new CheckBox(this);
        manageNight.setText("Set night brightness level");
        touchHeight(manageNight);
        root.addView(manageNight);
        nightLevelValue = label("");
        root.addView(nightLevelValue);
        nightLevel = levelSeekBar();
        root.addView(nightLevel);
        root.addView(label("Set auto transition times"));
        dayStart = new Button(this);
        touchHeight(dayStart);
        root.addView(dayStart);
        nightStart = new Button(this);
        touchHeight(nightStart);
        root.addView(nightStart);
        root.addView(text("Day and night times must differ. Scheduled mode forces stock Day/Night explicitly; it does not use the headlight/ILL Auto decision.", 14f));
        debug = new CheckBox(this);
        debug.setText("Debug logging");
        touchHeight(debug);
        root.addView(debug);
        Button apply = new Button(this);
        apply.setText("Apply");
        touchHeight(apply);
        apply.setOnClickListener(v -> apply());
        root.addView(apply);
        Button disable = new Button(this);
        disable.setText("Disable controller");
        touchHeight(disable);
        disable.setOnClickListener(v -> disableController());
        root.addView(disable);
        dayLevel.setOnSeekBarChangeListener(levelListener(dayLevelValue, "Day"));
        nightLevel.setOnSeekBarChangeListener(levelListener(nightLevelValue, "Night"));
        manageDay.setOnCheckedChangeListener((button, checked) -> dayLevel.setEnabled(checked));
        manageNight.setOnCheckedChangeListener((button, checked) -> nightLevel.setEnabled(checked));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { updateScheduleEnabled(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        dayStart.setOnClickListener(v -> pickTime(true));
        nightStart.setOnClickListener(v -> pickTime(false));
        return scroll;
    }

    private void load() {
        BrightnessPolicy.Config cfg = BrightnessConfig.read(this);
        enabled.setChecked(cfg.enabled);
        mode.setSelection(modeIndex(cfg.mode));
        manageDay.setChecked(cfg.dayLevel != BrightnessPolicy.PRESERVE_LEVEL);
        dayLevel.setProgress((cfg.dayLevel == BrightnessPolicy.PRESERVE_LEVEL ? 10 : cfg.dayLevel) - 1);
        dayLevel.setEnabled(manageDay.isChecked());
        manageNight.setChecked(cfg.nightLevel != BrightnessPolicy.PRESERVE_LEVEL);
        nightLevel.setProgress((cfg.nightLevel == BrightnessPolicy.PRESERVE_LEVEL ? 6 : cfg.nightLevel) - 1);
        nightLevel.setEnabled(manageNight.isChecked());
        dayStartMinute = cfg.dayStartMinute;
        nightStartMinute = cfg.nightStartMinute;
        debug.setChecked(cfg.debug);
        refreshTimeButtons();
        updateLevelLabel(dayLevelValue, "Day", dayLevel.getProgress() + 1);
        updateLevelLabel(nightLevelValue, "Night", nightLevel.getProgress() + 1);
        updateScheduleEnabled();
    }

    private void apply() {
        BrightnessPolicy.Config config = configFromUi(enabled.isChecked());
        if (config.mode == BrightnessPolicy.ControlMode.SET_AUTO && !config.scheduleValid()) {
            toast("Day and night transition times must differ.");
            return;
        }
        sendConfigRequest(config);
    }

    private void disableController() {
        sendConfigRequest(configFromUi(false));
    }

    private BrightnessPolicy.Config configFromUi(boolean enable) {
        return new BrightnessPolicy.Config(enable, selectedMode(),
                manageDay.isChecked() ? dayLevel.getProgress() + 1 : BrightnessPolicy.PRESERVE_LEVEL,
                manageNight.isChecked() ? nightLevel.getProgress() + 1 : BrightnessPolicy.PRESERVE_LEVEL,
                dayStartMinute, nightStartMinute, debug.isChecked());
    }

    private void sendConfigRequest(BrightnessPolicy.Config config) {
        if (pendingNonce != null) {
            toast("A brightness configuration request is already pending.");
            return;
        }
        String nonce = UUID.randomUUID().toString();
        Intent request = new Intent(BrightnessConfig.ACTION_APPLY)
                .setPackage(BrightnessConfig.SYSTEMUI_PACKAGE)
                .putExtra(BrightnessConfig.EXTRA_NONCE, nonce)
                .putExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER, configResultReceiver)
                .putExtra(BrightnessConfig.EXTRA_ENABLED, config.enabled)
                .putExtra(BrightnessConfig.EXTRA_MODE, config.mode.persisted)
                .putExtra(BrightnessConfig.EXTRA_DAY_LEVEL, config.dayLevel)
                .putExtra(BrightnessConfig.EXTRA_NIGHT_LEVEL, config.nightLevel)
                .putExtra(BrightnessConfig.EXTRA_DAY_START_MINUTE, config.dayStartMinute)
                .putExtra(BrightnessConfig.EXTRA_NIGHT_START_MINUTE, config.nightStartMinute)
                .putExtra(BrightnessConfig.EXTRA_DEBUG, config.debug);
        pendingNonce = nonce;
        bridgeStatus.setText("Configuration bridge: waiting for exact-gated SystemUI acknowledgement…");
        sendBroadcast(request);
        mainHandler.removeCallbacks(configTimeout);
        mainHandler.postDelayed(configTimeout, CONFIG_RESULT_TIMEOUT_MS);
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
        switch (mode.getSelectedItemPosition()) {
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
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateLevelLabel(view, label, progress + 1); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void updateLevelLabel(TextView view, String label, int value) { view.setText(label + " level: " + value + "/10"); }

    private TextView label(String value) { return text(value, 16f); }
    private TextView text(String value, float sp) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(sp); view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    private void touchHeight(View view) { view.setMinimumHeight(dp(56)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String formatMinute(int minute) {
        int safe = BrightnessPolicy.clampMinute(minute, 0);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
