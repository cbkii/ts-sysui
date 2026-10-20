package au.com.cb.ts18.statusbar.input;

/** Matches only the captured framework contrast WTF, never a whole DropBox tag. */
final class ContrastNoisePolicy {
    private static final String PREFIX = "background can not be translucent: #";

    private ContrastNoisePolicy() {}

    static boolean matches(String tag, String message) {
        if (!"ContrastColorUtil".equals(tag) || message == null || !message.startsWith(PREFIX))
            return false;
        int digits = message.length() - PREFIX.length();
        if (digits < 1 || digits > 8) return false;
        for (int i = PREFIX.length(); i < message.length(); i++) {
            char c = message.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
                return false;
        }
        return true;
    }
}
