package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SystemUiContractPolicyTest {
    @Test public void acceptsOnlyExactIdentity() {
        assertTrue(SystemUiContractPolicy.evaluate(
                29, "s9863a1h10", "ts18", SystemUiContractPolicy.EXPECTED_SHA256).supported);
    }

    @Test public void rejectsApiDeviceAndHashDrift() {
        assertFalse(SystemUiContractPolicy.evaluate(
                30, "s9863a1h10", "ts18", SystemUiContractPolicy.EXPECTED_SHA256).supported);
        assertFalse(SystemUiContractPolicy.evaluate(
                29, "other", "ts18", SystemUiContractPolicy.EXPECTED_SHA256).supported);
        assertFalse(SystemUiContractPolicy.evaluate(
                29, "s9863a1h10", "ts18", "00").supported);
    }
}
