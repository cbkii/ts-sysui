package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class NavActionTest {
    @Test
    public void missingSettingUsesSafeDefaultOrder() {
        assertEquals(Arrays.asList(
                NavAction.PREVIOUS, NavAction.PLAY_PAUSE, NavAction.NEXT),
                NavAction.parseConfigured(null));
    }

    @Test
    public void configuredOrderIsPreserved() {
        assertEquals(Arrays.asList(NavAction.NEXT, NavAction.PLAY_PAUSE),
                NavAction.parseConfigured("next,play_pause"));
    }

    @Test
    public void noneDisablesAllOptionalActions() {
        assertEquals(Collections.emptyList(), NavAction.parseConfigured("none"));
    }

    @Test
    public void unknownOrDuplicateActionFailsClosed() {
        assertTrue(NavAction.parseConfigured("next,unknown").isEmpty());
        assertTrue(NavAction.parseConfigured("next,next").isEmpty());
        assertTrue(NavAction.parseConfigured("next,").isEmpty());
    }
}
