package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Locale;

/** Normal-app diagnostics that do not depend on LSPosed or the SystemUI bridge. */
final class LocalSelfDiagnostics {
    private LocalSelfDiagnostics() {}

    static String collect(Context context, String bridgeState) {
        StringBuilder out = new StringBuilder();
        line(out, "local-diagnostic-build", Boolean.toString(BuildConfig.TS18_DIAGNOSTIC));
        line(out, "local-build-kind", BuildConfig.TS18_BUILD_KIND);
        line(out, "local-version", BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        line(out, "local-package", context == null ? "null" : context.getPackageName());
        line(out, "local-process", Build.VERSION.SDK_INT >= 28
                ? android.app.Application.getProcessName() : "api<28");
        line(out, "local-uid-pid", Process.myUid() + "/" + Process.myPid());
        line(out, "local-api-device-product",
                Build.VERSION.SDK_INT + " / " + Build.DEVICE + " / " + Build.PRODUCT);
        line(out, "bridge-state", bridgeState == null ? "unknown" : bridgeState);

        if (context == null) return out.toString();
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            line(out, "package-version",
                    info.versionName + "/" + (Build.VERSION.SDK_INT >= 28
                            ? info.getLongVersionCode() : info.versionCode));
            line(out, "package-source",
                    context.getApplicationInfo() == null
                            ? "unknown" : context.getApplicationInfo().sourceDir);
            line(out, "configure-permission",
                    pm.checkPermission(BrightnessConfig.CONFIGURE_PERMISSION,
                            context.getPackageName()) == PackageManager.PERMISSION_GRANTED
                            ? "GRANTED" : "DENIED");
            line(out, "signer-sha256", signerSha256(info));
        } catch (Throwable t) {
            line(out, "package-self-test",
                    "ERROR " + t.getClass().getSimpleName() + ":" + safe(t.getMessage()));
        }

        try {
            String[] scope = context.getResources().getStringArray(R.array.xposed_scope);
            line(out, "declared-xposed-scope", join(scope));
        } catch (Throwable t) {
            line(out, "declared-xposed-scope",
                    "ERROR " + t.getClass().getSimpleName());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("xposed_init")))) {
            StringBuilder entry = new StringBuilder();
            String value;
            while ((value = reader.readLine()) != null) {
                String clean = safe(value);
                if (clean.isEmpty()) continue;
                if (entry.length() > 0) entry.append(',');
                entry.append(clean);
                if (entry.length() > 512) break;
            }
            line(out, "xposed-init", entry.length() == 0 ? "EMPTY" : entry.toString());
        } catch (Throwable t) {
            line(out, "xposed-init", "ERROR " + t.getClass().getSimpleName());
        }

        line(out, "local-journal", DiagnosticJournal.snapshotText());
        return out.toString();
    }

    private static String signerSha256(PackageInfo info) throws Exception {
        if (info == null || info.signingInfo == null) return "unknown";
        Signature[] signatures = info.signingInfo.hasMultipleSigners()
                ? info.signingInfo.getApkContentsSigners()
                : info.signingInfo.getSigningCertificateHistory();
        if (signatures == null || signatures.length == 0) return "unknown";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest(signatures[0].toByteArray());
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "none";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(',');
            out.append(safe(value));
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }
}
