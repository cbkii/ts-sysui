package au.com.cb.ts18.statusbar.input;

import java.util.List;

/** Pure deterministic/sticky controller selection policy. */
final class NavMediaSelectionPolicy {
    enum Playback { PLAYING, PAUSED, STOPPED, OTHER, NONE }

    private NavMediaSelectionPolicy() {}

    static int choose(List<Candidate> candidates, int currentIndex) {
        if (candidates == null || candidates.isEmpty()) return -1;
        if (isUsable(candidates, currentIndex)
                && candidates.get(currentIndex).playback == Playback.PLAYING) {
            return currentIndex;
        }
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.usable && candidate.playback == Playback.PLAYING) return i;
        }
        if (isUsable(candidates, currentIndex)) return currentIndex;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.usable && candidate.playback == Playback.PAUSED) return i;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).usable) return i;
        }
        return -1;
    }

    private static boolean isUsable(List<Candidate> candidates, int index) {
        return index >= 0 && index < candidates.size() && candidates.get(index).usable;
    }

    static final class Candidate {
        final Playback playback;
        final boolean usable;

        Candidate(Playback playback, boolean usable) {
            this.playback = playback;
            this.usable = usable;
        }
    }
}
