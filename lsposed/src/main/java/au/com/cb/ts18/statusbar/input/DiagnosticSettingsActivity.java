package au.com.cb.ts18.statusbar.input;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only diagnostic console exposed only by the diagnostic source-set manifest.
 * It does not arm or mutate compact/nav/brightness policy.
 */
public final class DiagnosticSettingsActivity extends Activity {
    private static final long BRIDGE_TIMEOUT_MS = 4000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService diagnostics = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TS18-DiagnosticReport");
        thread.setDaemon(true);
        return thread;
    });
    private TextView status;
    private TextView report;
    private Button copyButton;
    private Button saveButton;
    private String pendingNonce;
    private int renderGeneration;
    private boolean reportReady;

    private final ResultReceiver receiver = new ResultReceiver(main) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            String nonce = data == null ? null
                    : data.getString(BrightnessConfig.EXTRA_NONCE);
            if (pendingNonce == null || !pendingNonce.equals(nonce)) return;
            pendingNonce = null;
            main.removeCallbacks(timeout);
            boolean success = data != null
                    && data.getBoolean(SystemUiBridge.EXTRA_SUCCESS, false);
            String detail = data == null ? "no bundle"
                    : data.getString(SystemUiBridge.EXTRA_DETAIL, "no detail");
            status.setText("SystemUI bridge: " + (success ? "REPLIED" : "REJECTED")
                    + " · " + detail);
            render(data, success ? "bridge-replied" : "bridge-rejected");
        }
    };

    private final Runnable timeout = () -> {
        if (pendingNonce == null) return;
        pendingNonce = null;
        status.setText("SystemUI bridge: TIMEOUT — use local self-test and LSPosed/logcat evidence.");
        render(null, "bridge-timeout");
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("TS18 Diagnostic Console");
        setContentView(buildUi());
        DiagnosticJournal.state("diagnostic-activity", "CREATED",
                "build=" + BuildConfig.TS18_BUILD_KIND);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null && pendingNonce == null) refresh();
    }

    @Override protected void onStop() {
        // Invalidate any report worker from the previous visible Activity lifetime.
        // A resumed refresh must never be overwritten by a stale callback.
        renderGeneration++;
        setReportReady(false);
        main.removeCallbacks(timeout);
        pendingNonce = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        renderGeneration++;
        diagnostics.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(text("TS18 Diagnostic Console", 25f));
        TextView warning = text(BuildConfig.TS18_DIAGNOSTIC
                ? "DIAGNOSTIC BUILD — verbose bounded runtime logging is forced on. "
                + "This console is read-only; use the normal TS18 System UI screen for configuration."
                : "WARNING: diagnostic console was launched from a non-diagnostic build.",
                14f);
        root.addView(warning);

        status = text("SystemUI bridge: not queried", 14f);
        root.addView(status);

        Button refresh = button("Refresh full diagnostic status");
        refresh.setOnClickListener(v -> refresh());
        root.addView(refresh);

        copyButton = button("Copy report");
        copyButton.setOnClickListener(v -> copy());
        root.addView(copyButton);

        saveButton = button("Save report to Downloads");
        saveButton.setOnClickListener(v -> save());
        root.addView(saveButton);

        report = text("No report yet.", 12f);
        report.setTextIsSelectable(true);
        root.addView(report);
        setReportReady(false);
        return scroll;
    }

    private void refresh() {
        if (pendingNonce != null) return;
        setReportReady(false);
        report.setText("Waiting for SystemUI bridge status...");
        DiagnosticJournal.record("INFO", "diagnostic-activity", "status refresh requested");
        String nonce = UUID.randomUUID().toString();
        Intent request = new Intent(SystemUiBridge.ACTION_QUERY_STATUS)
                .setPackage(BrightnessConfig.SYSTEMUI_PACKAGE)
                .putExtra(BrightnessConfig.EXTRA_NONCE, nonce)
                .putExtra(BrightnessConfig.EXTRA_RESULT_RECEIVER, receiver);
        pendingNonce = nonce;
        status.setText("SystemUI bridge: requesting...");
        try {
            sendBroadcast(request);
        } catch (Throwable t) {
            pendingNonce = null;
            main.removeCallbacks(timeout);
            status.setText("SystemUI bridge: SEND FAILED · " + t.getClass().getSimpleName());
            DiagnosticJournal.failure("diagnostic-bridge-send", "sendBroadcast failed", t);
            render(null, "bridge-send-" + t.getClass().getSimpleName());
            return;
        }
        main.removeCallbacks(timeout);
        main.postDelayed(timeout, BRIDGE_TIMEOUT_MS);
    }

    private void render(Bundle state, String bridgeState) {
        final int generation = ++renderGeneration;
        final Bundle snapshot = state == null ? null : new Bundle(state);
        Context application = getApplicationContext();
        final Context localContext = application == null ? this : application;
        setReportReady(false);
        report.setText("Collecting local diagnostics off the UI thread...");
        diagnostics.execute(() -> {
            String rendered;
            try {
                String local = LocalSelfDiagnostics.collect(localContext, bridgeState);
                rendered = buildReport(snapshot, local);
            } catch (Throwable t) {
                DiagnosticJournal.failure("diagnostic-render",
                        "background report generation failed", t);
                rendered = "Diagnostic report generation failed: "
                        + t.getClass().getSimpleName() + "\n";
            }
            final String result = rendered;
            main.post(() -> {
                if (generation != renderGeneration || isFinishing() || isDestroyed()) return;
                report.setText(result);
                setReportReady(true);
            });
        });
    }

    private static String buildReport(Bundle s, String localDiagnostics) {
        StringBuilder out = new StringBuilder();
        out.append("=== LOCAL APK SELF-TEST ===\n");
        out.append(localDiagnostics == null ? "[local diagnostics unavailable]\n" : localDiagnostics);
        out.append('\n');

        if (s == null) {
            out.append("=== SYSTEMUI BRIDGE ===\nNO RESPONSE\n");
            return out.toString();
        }

        out.append("=== SYSTEMUI BRIDGE / BUILD ===\n");
        line(out, "bridge-ready", s.getBoolean("bridge_ready"));
        line(out, "bridge-install-detail", s.getString("bridge_install_detail"));
        line(out, "identity", s.getString("identity_state") + " / "
                + s.getString("identity_detail"));
        line(out, "diagnostic-build", s.getBoolean("diagnostic_build"));
        line(out, "diagnostic-build-kind", s.getString("diagnostic_build_kind"));
        line(out, "diagnostic-version", s.getString("diagnostic_version_name") + "/"
                + s.getInt("diagnostic_version_code"));
        line(out, "diagnostic-journal-entries", s.getInt("diagnostic_journal_entries"));
        line(out, "diagnostic-journal-dropped", s.getInt("diagnostic_journal_dropped"));

        out.append("\n=== HOOK / INSTALL STATE ===\n");
        line(out, "nav-hook-installed", s.getBoolean("nav_hook_installed"));
        line(out, "brightness-hook-count", s.getInt("brightness_hook_count"));
        line(out, "stage-summary", s.getString("diagnostic_stage_summary"));

        out.append("\n=== RRO / RESOLVED RESOURCES ===\n");
        line(out, "geometry-overlay-mounted", s.getBoolean("geometry_overlay_mounted"));
        line(out, "visual-overlay-mounted", s.getBoolean("visual_overlay_mounted"));
        line(out, "resolved-status-bar-height-px", s.getInt("resolved_status_bar_height_px", -1));
        line(out, "resolved-status-icon-size-px", s.getInt("resolved_status_icon_size_px", -1));
        line(out, "resolved-status-icon-drawing-size-px",
                s.getInt("resolved_status_icon_drawing_size_px", -1));
        line(out, "resolved-status-clock-size-px", s.getInt("resolved_status_clock_size_px", -1));
        line(out, "resolved-density", s.getFloat("resolved_density", -1f));

        out.append("\n=== COMPACT TOUCH ===\n");
        line(out, "compact-enabled", s.getBoolean("compact_enabled"));
        line(out, "compact-input-enabled", s.getBoolean("compact_input_enabled"));
        line(out, "compact-adapter", s.getString("compact_adapter"));
        line(out, "compact-fraction", s.getFloat("compact_fraction"));
        line(out, "compact-corner-gap-px", s.getInt("compact_corner_gap_px"));

        out.append("\n=== RIGHT NAV ===\n");
        line(out, "nav-enabled", s.getBoolean("nav_enabled"));
        line(out, "nav-root-seen", s.getBoolean("nav_root_seen"));
        line(out, "nav-root-attached", s.getBoolean("nav_root_attached"));
        line(out, "nav-host-seen", s.getBoolean("nav_host_seen"));
        line(out, "nav-preflight", s.getString("nav_preflight_reason"));
        line(out, "nav-host-px", s.getInt("nav_host_width_px") + "x"
                + s.getInt("nav_host_height_px"));
        line(out, "nav-density", s.getFloat("nav_density"));
        line(out, "nav-projected-cell-px", s.getInt("nav_projected_cell_px"));
        line(out, "nav-horizontal-floor-px", s.getInt("nav_horizontal_floor_px"));
        line(out, "nav-horizontal-preferred-px", s.getInt("nav_horizontal_preferred_px"));
        line(out, "nav-stock-summary", s.getString("nav_stock_summary"));
        line(out, "nav-injected-actions", s.getString("nav_injected_actions"));
        line(out, "nav-media-controller-count", s.getInt("nav_media_controller_count"));
        line(out, "nav-media-selected-package", s.getString("nav_media_selected_package"));
        line(out, "nav-media-playback-state", s.getInt("nav_media_playback_state"));
        line(out, "nav-media-action-bits",
                "0x" + Long.toHexString(s.getLong("nav_media_action_bits")));
        line(out, "nav-breaker", "open=" + s.getBoolean("nav_breaker_open")
                + " failures=" + s.getInt("nav_failure_count"));

        out.append("\n=== TOPWAY BRIGHTNESS ===\n");
        line(out, "brightness-controller-attached",
                s.getBoolean("brightness_controller_attached"));
        line(out, "brightness-compatible", s.getBoolean("brightness_compatible"));
        line(out, "brightness-transport-ready", s.getBoolean("brightness_transport_ready"));
        line(out, "brightness-mode-known", s.getBoolean("brightness_mode_known"));
        line(out, "brightness-levels-known", s.getBoolean("brightness_levels_known"));
        line(out, "brightness-topway-mode", s.getInt("brightness_topway_mode", -1));
        line(out, "brightness-effective-night", s.getBoolean("brightness_effective_night"));
        line(out, "brightness-day-level", s.getInt("brightness_detected_day_level", -1));
        line(out, "brightness-night-level", s.getInt("brightness_detected_night_level", -1));
        line(out, "brightness-callback-258",
                s.getLong("brightness_last_258_callback_at"));
        line(out, "brightness-callback-516",
                s.getLong("brightness_last_516_callback_at"));
        line(out, "brightness-stock-write", s.getString("brightness_last_stock_write"));
        line(out, "brightness-module-action", s.getString("brightness_last_module_action"));
        line(out, "brightness-pending-action", s.getString("brightness_pending_action"));
        line(out, "brightness-confirmation", s.getString("brightness_confirmation"));
        line(out, "brightness-runtime-state", s.getString("brightness_runtime_state"));
        line(out, "brightness-breaker", "open=" + s.getBoolean("brightness_breaker_open")
                + " failures=" + s.getInt("brightness_failure_count"));

        out.append("\n=== SYSTEMUI DIAGNOSTIC JOURNAL ===\n");
        String journal = s.getString("diagnostic_journal");
        out.append(journal == null || journal.trim().isEmpty()
                ? "[journal unavailable]\n" : journal);
        return out.toString();
    }

    private void copy() {
        if (!reportReady) {
            toast("Diagnostic report is still being collected.");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Clipboard unavailable.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "TS18 diagnostic report", report.getText()));
        toast("Diagnostic report copied.");
    }

    private void save() {
        if (!reportReady) {
            toast("Diagnostic report is still being collected.");
            return;
        }
        String value = report.getText().toString();
        if (value.trim().isEmpty()) {
            toast("No diagnostic report to save.");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "ts18-sysui-diagnostic-" + stamp + ".txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                "Download/TS18-SystemUI");
        Uri uri = null;
        try {
            uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("output stream unavailable");
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            toast("Saved to Downloads/TS18-SystemUI.");
        } catch (Throwable t) {
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
            DiagnosticJournal.failure("diagnostic-save", "MediaStore export failed", t);
            toast("Save failed: " + t.getClass().getSimpleName());
        }
    }

    private void setReportReady(boolean ready) {
        reportReady = ready;
        if (copyButton != null) copyButton.setEnabled(ready);
        if (saveButton != null) saveButton.setEnabled(ready);
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setMinimumHeight(dp(56));
        return button;
    }

    private TextView text(String value, float sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private static void line(StringBuilder out, String key, Object value) {
        out.append(key).append('=').append(value == null ? "" : value).append('\n');
    }
}
