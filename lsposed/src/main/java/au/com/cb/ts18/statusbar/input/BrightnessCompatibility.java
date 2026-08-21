package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.os.Build;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

final class BrightnessCompatibility {
    static final String EXPECTED_SYSTEMUI_SHA256 =
            "668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f";

    private BrightnessCompatibility() {}

    static Result verify(Context context) {
        if (Build.VERSION.SDK_INT != 29) return Result.fail("expected API 29, got " + Build.VERSION.SDK_INT);
        String device = Build.DEVICE == null ? "" : Build.DEVICE;
        String product = Build.PRODUCT == null ? "" : Build.PRODUCT;
        if (!device.contains("s9863a1h10") && !product.contains("s9863a1h10")) {
            return Result.fail("unexpected device/product " + device + '/' + product);
        }
        String source = context.getApplicationInfo().sourceDir;
        if (source == null || source.trim().isEmpty()) return Result.fail("SystemUI sourceDir unavailable");
        try {
            String actual = sha256(source);
            if (!EXPECTED_SYSTEMUI_SHA256.equals(actual)) return Result.fail("SystemUI SHA-256 mismatch: " + actual);
            return Result.ok(actual);
        } catch (Throwable t) {
            return Result.fail("SystemUI hash failed: " + t.getClass().getSimpleName());
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
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    static final class Result {
        final boolean compatible;
        final String detail;
        private Result(boolean compatible, String detail) { this.compatible = compatible; this.detail = detail; }
        static Result ok(String detail) { return new Result(true, detail); }
        static Result fail(String detail) { return new Result(false, detail); }
    }
}
