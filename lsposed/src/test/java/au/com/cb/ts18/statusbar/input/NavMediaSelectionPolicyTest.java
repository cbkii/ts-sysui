package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class NavMediaSelectionPolicyTest {
    @Test public void playingControllerWinsAndRemainsSticky() {
        assertEquals(1, NavMediaSelectionPolicy.choose(Arrays.asList(
                candidate(NavMediaSelectionPolicy.Playback.PAUSED, true),
                candidate(NavMediaSelectionPolicy.Playback.PLAYING, true)), 0));
        assertEquals(1, NavMediaSelectionPolicy.choose(Arrays.asList(
                candidate(NavMediaSelectionPolicy.Playback.PLAYING, true),
                candidate(NavMediaSelectionPolicy.Playback.PLAYING, true)), 1));
    }

    @Test public void pausedCurrentRemainsWhenNothingIsPlaying() {
        assertEquals(1, NavMediaSelectionPolicy.choose(Arrays.asList(
                candidate(NavMediaSelectionPolicy.Playback.PAUSED, true),
                candidate(NavMediaSelectionPolicy.Playback.PAUSED, true)), 1));
    }

    @Test public void noUsableControllerReturnsNone() {
        assertEquals(-1, NavMediaSelectionPolicy.choose(Collections.singletonList(
                candidate(NavMediaSelectionPolicy.Playback.PLAYING, false)), 0));
        assertEquals(-1, NavMediaSelectionPolicy.choose(Collections.emptyList(), -1));
    }

    private static NavMediaSelectionPolicy.Candidate candidate(
            NavMediaSelectionPolicy.Playback playback, boolean usable) {
        return new NavMediaSelectionPolicy.Candidate(playback, usable);
    }
}
