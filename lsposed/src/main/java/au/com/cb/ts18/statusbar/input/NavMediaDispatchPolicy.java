package au.com.cb.ts18.statusbar.input;

/** Pure exactly-once transport-command decision. */
final class NavMediaDispatchPolicy {
    enum Playback { PLAYING, PAUSED, STOPPED, OTHER, NONE }
    enum Command { NONE, PREVIOUS, PLAY, PAUSE, NEXT }

    private NavMediaDispatchPolicy() {}

    static Command decide(NavAction action,
                          Playback playback,
                          boolean supportsPrevious,
                          boolean supportsPlay,
                          boolean supportsPause,
                          boolean supportsNext) {
        if (action == null) return Command.NONE;
        switch (action) {
            case PREVIOUS:
                return supportsPrevious ? Command.PREVIOUS : Command.NONE;
            case NEXT:
                return supportsNext ? Command.NEXT : Command.NONE;
            case PLAY_PAUSE:
                if (playback == Playback.PLAYING) {
                    return supportsPause ? Command.PAUSE : Command.NONE;
                }
                if (playback == Playback.PAUSED || playback == Playback.STOPPED) {
                    return supportsPlay ? Command.PLAY : Command.NONE;
                }
                return Command.NONE;
            default:
                return Command.NONE;
        }
    }
}
