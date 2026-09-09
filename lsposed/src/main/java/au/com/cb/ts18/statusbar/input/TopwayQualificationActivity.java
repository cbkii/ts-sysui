package au.com.cb.ts18.statusbar.input;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

/** Explicit diagnostic-only XTService media qualification harness. */
public final class TopwayQualificationActivity extends Activity {
    private static final long TIMEOUT_MS = 4000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView report;
    private String pendingNonce;

    private final ResultReceiver receiver = new ResultReceiver(main) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            String nonce = data == null ? null : data.getString(BrightnessConfig.EXTRA_NONCE);
            if (pendingNonce == null || !pendingNonce.equals(nonce)) return;
            pendingNonce = null;
            main.removeCallbacks(timeout);
            boolean ok = data != null && data.getBoolean(XtServiceDiagnosticBridge.EXTRA_SUCCESS, false);
            String detail = data == null ? "no response bundle"
                    : data.getString(XtServiceDiagnosticBridge.EXTRA_DETAIL, "no detail");
            status.setText((ok ? "Result: OK · " : "Result: BLOCKED/FAILED · ") + detail);
            render(data);
        }
    };

    private final Runnable timeout = () -> {
        if (pendingNonce == null) return;
        pendingNonce = null;
        status.setText("Result: TIMEOUT — inspect SystemUI/LSPosed diagnostics.");
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("TS18 Topway Qualification");
        setContentView(buildUi());
        queryStatus();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(timeout);
        pendingNonce = null;
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = text("Exact XTService qualification", 24f);
        root.addView(title);
        root.addView(text("DIAGNOSTIC BUILD ONLY. These buttons issue exactly one explicit vendor Binder media command. A successful Binder return does not prove playback changed. Normal right-nav playback remains Android MediaController-only.", 14f));

        status = text("No request yet.", 14f);
        root.addView(status);
        Button refresh = button("Refresh observer status");
        refresh.setOnClickListener(v -> queryStatus());
        root.addView(refresh);
        root.addView(actionButton("Vendor Previous", "previous"));
        root.addView(actionButton("Vendor Play", "play"));
        root.addView(actionButton("Vendor Pause", "pause"));
        root.addView(actionButton("Vendor Next", "next"));

        report = text("Waiting for status…", 12f);
        report.setTextIsSelectable(true);
        root.addView(report);
        return scroll;
    }

    private Button actionButton(String label, String action) {
        Button button = button(label);
        button.setOnClickListener(v -> qualify(action));
        return button;
    }

    private void queryStatus() {
        send(new Intent(XtServiceDiagnosticBridge.ACTION_QUERY), null);
    }

    private void qualify(String mediaAction) {
        send(new Intent(XtServiceDiagnosticBridge.ACTION_QUALIFY_MEDIA), mediaAction);
    }

    private void send(Intent request, String mediaAction) {
        if (pendingNonce != null) {
            toast("An auxiliary SystemUI request is already pending.");
            return;
        }
        String nonce = UUID.randomUUID().toString();
        request.setPackage(BrightnessConfig.SYSTEMUI_PACKAGE)
                .putExtra(BrightnessConfig.EXTRA_NONCE, nonce)
                .putExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER, receiver);
        if (mediaAction != null) {
            request.putExtra(XtServiceDiagnosticBridge.EXTRA_MEDIA_ACTION, mediaAction);
            status.setText("Sending exactly one vendor " + mediaAction + " qualification command…");
        } else {
            status.setText("Requesting auxiliary status…");
        }
        pendingNonce = nonce;
        try {
            sendBroadcast(request);
            main.removeCallbacks(timeout);
            main.postDelayed(timeout, TIMEOUT_MS);
        } catch (Throwable t) {
            pendingNonce = null;
            status.setText("Send failed: " + t.getClass().getSimpleName());
        }
    }

    private void render(Bundle s) {
        if (s == null) { report.setText("No status bundle."); return; }
        StringBuilder out = new StringBuilder();
        line(out, "SystemUI identity", s.getString("identity_state") + " / " + s.getString("identity_detail"));
        line(out, "XTService identity", s.getString("xtservice_identity_state") + " / " + s.getString("xtservice_identity_detail"));
        line(out, "XTService SHA", "expected=" + s.getString("xtservice_expected_sha256") + " actual=" + s.getString("xtservice_actual_sha256"));
        line(out, "XTService bind", s.getString("xtservice_bind_state") + " callback=" + s.getBoolean("xtservice_callback_registered"));
        line(out, "Reverse", "known=" + s.getBoolean("xtservice_reverse_known") + " status=" + s.getInt("xtservice_reverse_status", -1) + " at=" + s.getLong("xtservice_reverse_at"));
        line(out, "Sleep", "known=" + s.getBoolean("xtservice_sleep_known") + " status=" + s.getInt("xtservice_sleep_status", -1) + " at=" + s.getLong("xtservice_sleep_at"));
        line(out, "Vehicle nav veto", s.getBoolean("xtservice_vehicle_veto") + " / " + s.getString("xtservice_vehicle_policy"));
        line(out, "XTService breaker", "open=" + s.getBoolean("xtservice_breaker_open") + " failures=" + s.getInt("xtservice_failure_count"));
        line(out, "Stock nav config", "navigationbar_config=" + s.getString("stock_nav_navigationbar_config") + ", show_navigationbar=" + s.getString("stock_nav_show_navigationbar") + ", persist.navibar.position=" + s.getString("stock_nav_persist_navibar_position"));
        line(out, "Stock nav config change", s.getLong("stock_nav_config_last_change_at") + " / " + s.getString("stock_nav_config_last_change_reason"));
        line(out, "Right-nav preflight", s.getString("nav_preflight_reason"));
        line(out, "Brightness physical", "raw=" + s.getInt("brightness_screen_raw", -1) + " backend=" + s.getString("brightness_physical_backend"));
        line(out, "Brightness setting chronology", s.getInt("brightness_setting_previous_raw", -1) + " -> " + s.getInt("brightness_setting_changed_raw", -1) + " at=" + s.getLong("brightness_setting_last_change_at"));
        line(out, "Brightness correlation", s.getString("brightness_setting_correlation") + " deltaMs=" + s.getLong("brightness_setting_correlation_delta_ms") + " · " + s.getString("brightness_setting_correlation_note"));
        line(out, "Last vendor qualification", s.getString("xtservice_last_qualification_action") + " at=" + s.getLong("xtservice_last_qualification_at") + " binderSuccess=" + s.getBoolean("xtservice_last_qualification_binder_success") + " · " + s.getString("xtservice_last_qualification_detail"));
        report.setText(out.toString());
    }

    private static void line(StringBuilder out, String label, Object value) {
        out.append(label).append(": ").append(value == null ? "unknown" : value).append('\n');
    }

    private TextView text(String value, float sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        int gap = dp(8);
        view.setPadding(0, gap, 0, gap);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
