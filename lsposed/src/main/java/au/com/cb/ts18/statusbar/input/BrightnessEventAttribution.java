package au.com.cb.ts18.statusbar.input;

/**
 * Bounded temporal correlation only. It deliberately does not claim causal
 * writer identity from a Settings observer callback. Classification output is
 * bounded temporal correlation only unless an unambiguous module write precedes
 * the matching SCREEN_BRIGHTNESS event.
 */
final class BrightnessEventAttribution {
    static final long CORRELATION_WINDOW_MS = 1500L;

    private BrightnessEventAttribution() {}

    static Result classify(long eventAt, int raw,
                           long moduleWriteAt, int moduleRequestedRaw,
                           long stockTopwayWriteAt, String stockTopwayWrite,
                           long callback258At, long callback516At) {
        // Prefer the conservative external-writer label whenever a recorded stock
        // Topway write also precedes the Settings event inside the bounded window.
        if (precededByWithin(eventAt, stockTopwayWriteAt)) {
            return new Result("external-screen-change-near-stock-topway-write:"
                    + safe(stockTopwayWrite), eventAt - stockTopwayWriteAt);
        }
        if (raw == moduleRequestedRaw && precededByWithin(eventAt, moduleWriteAt)) {
            return new Result("module-screen-brightness-write",
                    eventAt - moduleWriteAt);
        }
        if (near(eventAt, callback516At)) {
            return new Result("external-screen-change-near-topway-516-callback",
                    eventAt - callback516At);
        }
        if (near(eventAt, callback258At)) {
            return new Result("external-screen-change-near-topway-258-callback",
                    eventAt - callback258At);
        }
        return new Result("external-or-unknown-writer", Long.MIN_VALUE);
    }

    private static boolean precededByWithin(long eventAt, long candidateAt) {
        return eventAt > 0L && candidateAt > 0L && candidateAt <= eventAt
                && eventAt - candidateAt <= CORRELATION_WINDOW_MS;
    }

    private static boolean near(long eventAt, long candidateAt) {
        return eventAt > 0L && candidateAt > 0L
                && Math.abs(eventAt - candidateAt) <= CORRELATION_WINDOW_MS;
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        String clean = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return clean.length() <= 80 ? clean : clean.substring(0, 80);
    }

    static final class Result {
        final String classification;
        final long deltaMs;

        Result(String classification, long deltaMs) {
            this.classification = classification;
            this.deltaMs = deltaMs;
        }
    }
}
