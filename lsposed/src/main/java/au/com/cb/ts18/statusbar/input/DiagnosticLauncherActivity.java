package au.com.cb.ts18.statusbar.input;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Diagnostic-build launcher that requires an explicit user tap before opening private tools. */
public final class DiagnosticLauncherActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.TS18_DIAGNOSTIC) {
            finish();
            return;
        }
        setTitle("TS18 Diagnostics");
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("TS18 Diagnostic Tools");
        title.setTextSize(24f);
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Open one diagnostic surface explicitly. The Topway qualification harness is not exported and cannot be launched directly by another app.");
        note.setTextSize(14f);
        root.addView(note);

        Button console = new Button(this);
        console.setText("Open Diagnostic Console");
        console.setOnClickListener(v -> startActivity(
                new Intent(this, DiagnosticSettingsActivity.class)));
        root.addView(console);

        Button topway = new Button(this);
        topway.setText("Open Topway Qualification");
        topway.setOnClickListener(v -> startActivity(
                new Intent(this, TopwayQualificationActivity.class)));
        root.addView(topway);

        return root;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density));
    }
}
