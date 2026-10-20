package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContrastNoisePolicyTest {
    @Test public void onlyTheCapturedContrastWarningIsMatched() {
        assertTrue(ContrastNoisePolicy.matches("ContrastColorUtil", "background can not be translucent: #cd4f4e4e"));
        assertTrue(ContrastNoisePolicy.matches("ContrastColorUtil", "background can not be translucent: #0"));
        assertFalse(ContrastNoisePolicy.matches("Other", "background can not be translucent: #cd4f4e4e"));
        assertFalse(ContrastNoisePolicy.matches("ContrastColorUtil", "different failure"));
        assertFalse(ContrastNoisePolicy.matches("ContrastColorUtil", "background can not be translucent: #cd4f4e4e\nother failure"));
        assertFalse(ContrastNoisePolicy.matches("ContrastColorUtil", "background can not be translucent: #wrong"));
        assertFalse(ContrastNoisePolicy.matches("ContrastColorUtil", null));
        assertFalse(ContrastNoisePolicy.matches(null, null));
    }
}
