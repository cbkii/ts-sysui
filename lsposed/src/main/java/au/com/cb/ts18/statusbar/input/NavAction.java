package au.com.cb.ts18.statusbar.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

enum NavAction {
    PREVIOUS("previous"),
    PLAY_PAUSE("play_pause"),
    NEXT("next");

    private final String id;

    NavAction(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    static List<NavAction> defaults() {
        return Collections.unmodifiableList(
                Arrays.asList(PREVIOUS, PLAY_PAUSE, NEXT));
    }

    static List<NavAction> parseConfigured(String raw) {
        if (raw == null) return defaults();
        String value = raw.trim();
        if ("none".equals(value)) return Collections.emptyList();
        if (value.isEmpty()) return Collections.emptyList();

        Set<NavAction> ordered = new LinkedHashSet<>();
        String[] tokens = value.split(",", -1);
        for (String token : tokens) {
            NavAction action = fromId(token.trim());
            if (action == null || !ordered.add(action)) {
                // Explicit malformed configuration fails closed rather than guessing.
                return Collections.emptyList();
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    private static NavAction fromId(String id) {
        for (NavAction action : values()) {
            if (action.id.equals(id)) return action;
        }
        return null;
    }
}
