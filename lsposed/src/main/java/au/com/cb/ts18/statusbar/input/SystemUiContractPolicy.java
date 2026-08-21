package au.com.cb.ts18.statusbar.input;

import java.util.Locale;

/** Pure identity gate shared by runtime and JVM tests. */
final class SystemUiContractPolicy {
    static final int EXPECTED_API = 29;
    static final String EXPECTED_DEVICE_TOKEN = "s9863a1h10";
    static final String EXPECTED_SHA256 =
            "668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f";

    private SystemUiContractPolicy() {}

    static Decision evaluate(int api, String device, String product, String actualSha256) {
        if (api != EXPECTED_API) return Decision.reject("api");
        String identity = safe(device) + '/' + safe(product);
        if (!identity.contains(EXPECTED_DEVICE_TOKEN)) return Decision.reject("device");
        String hash = safe(actualSha256).toLowerCase(Locale.ROOT);
        if (!EXPECTED_SHA256.equals(hash)) return Decision.reject("sha256");
        return Decision.accept();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Decision {
        final boolean supported;
        final String reason;

        private Decision(boolean supported, String reason) {
            this.supported = supported;
            this.reason = reason;
        }

        static Decision accept() {
            return new Decision(true, "exact-contract");
        }

        static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }
}
