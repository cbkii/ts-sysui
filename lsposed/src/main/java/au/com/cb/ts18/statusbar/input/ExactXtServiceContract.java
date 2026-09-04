package au.com.cb.ts18.statusbar.input;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** Exact supplied XTService identity gate. Verification is always performed off main. */
final class ExactXtServiceContract {
    static final String PACKAGE = "com.tw.service.xt";
    static final String SERVICE_CLASS = "com.tw.service.xt.CommandService";
    static final String BIND_ACTION = "com.tw.service.xt.CommandService.Bind";
    static final String EXPECTED_APK_SHA256 =
            "341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267";

    private ExactXtServiceContract() {}

    static Intent bindIntent() {
        return new Intent(BIND_ACTION).setComponent(new ComponentName(PACKAGE, SERVICE_CLASS));
    }

    static Result verifyInstalled(Context context) {
        if (context == null) return Result.unsupported("context-unavailable");
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo app = pm.getApplicationInfo(PACKAGE, 0);
            if (app == null || app.sourceDir == null || app.sourceDir.trim().isEmpty()) {
                return Result.unsupported("xtservice-source-unavailable");
            }
            pm.getServiceInfo(new ComponentName(PACKAGE, SERVICE_CLASS), 0);
            List<ResolveInfo> resolved = pm.queryIntentServices(bindIntent(), 0);
            boolean exactResolver = false;
            if (resolved != null) {
                for (ResolveInfo info : resolved) {
                    if (info != null && info.serviceInfo != null
                            && PACKAGE.equals(info.serviceInfo.packageName)
                            && SERVICE_CLASS.equals(info.serviceInfo.name)) {
                        exactResolver = true;
                        break;
                    }
                }
            }
            if (!exactResolver) return Result.unsupported("exact-bind-action-not-resolved");

            String actual = sha256(app.sourceDir);
            PackageInfo pkg = pm.getPackageInfo(PACKAGE, 0);
            String version = pkg == null ? "unknown" : pkg.versionName;
            if (!EXPECTED_APK_SHA256.equalsIgnoreCase(actual)) {
                return new Result(false, "apk-sha256-mismatch", actual, version);
            }
            return new Result(true, "exact-supplied-xtservice", actual, version);
        } catch (PackageManager.NameNotFoundException e) {
            return Result.unsupported("xtservice-component-not-found");
        } catch (Throwable t) {
            return Result.unsupported("identity-check-" + t.getClass().getSimpleName());
        }
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }

    static final class Result {
        final boolean supported;
        final String detail;
        final String actualSha256;
        final String versionName;

        Result(boolean supported, String detail, String actualSha256, String versionName) {
            this.supported = supported;
            this.detail = detail;
            this.actualSha256 = actualSha256 == null ? "unknown" : actualSha256;
            this.versionName = versionName == null ? "unknown" : versionName;
        }

        static Result unsupported(String detail) {
            return new Result(false, detail, "unknown", "unknown");
        }
    }
}
