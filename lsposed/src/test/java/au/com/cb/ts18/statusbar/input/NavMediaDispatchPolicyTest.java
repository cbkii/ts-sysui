package au.com.cb.ts18.statusbar.input;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NavMediaDispatchPolicyTest {
    @Test public void eachActionMapsToAtMostOneSupportedCommand() {
        assertEquals(NavMediaDispatchPolicy.Command.PREVIOUS,
                decide(NavAction.PREVIOUS, NavMediaDispatchPolicy.Playback.PLAYING,
                        true, true, true, true));
        assertEquals(NavMediaDispatchPolicy.Command.NEXT,
                decide(NavAction.NEXT, NavMediaDispatchPolicy.Playback.PLAYING,
                        true, true, true, true));
        assertEquals(NavMediaDispatchPolicy.Command.PAUSE,
                decide(NavAction.PLAY_PAUSE, NavMediaDispatchPolicy.Playback.PLAYING,
                        true, true, true, true));
        assertEquals(NavMediaDispatchPolicy.Command.PLAY,
                decide(NavAction.PLAY_PAUSE, NavMediaDispatchPolicy.Playback.PAUSED,
                        true, true, true, true));
    }

    @Test public void unsupportedOrAmbiguousStateSendsNothing() {
        assertEquals(NavMediaDispatchPolicy.Command.NONE,
                decide(NavAction.NEXT, NavMediaDispatchPolicy.Playback.PLAYING,
                        true, true, true, false));
        assertEquals(NavMediaDispatchPolicy.Command.NONE,
                decide(NavAction.PLAY_PAUSE, NavMediaDispatchPolicy.Playback.OTHER,
                        true, true, true, true));
        assertEquals(NavMediaDispatchPolicy.Command.NONE,
                decide(NavAction.PLAY_PAUSE, NavMediaDispatchPolicy.Playback.PAUSED,
                        true, false, true, true));
    }

    private static NavMediaDispatchPolicy.Command decide(
            NavAction action, NavMediaDispatchPolicy.Playback playback,
            boolean previous, boolean play, boolean pause, boolean next) {
        return NavMediaDispatchPolicy.decide(
                action, playback, previous, play, pause, next);
    }
}
