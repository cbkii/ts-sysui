package au.com.cb.ts18.statusbar.input;

/** Exact current Topway protocol recovered from the supplied SystemUI/CarSetting APKs. */
final class BrightnessProtocol {
    static final int COMMAND_MODE = 258;
    static final int COMMAND_BRIGHTNESS = 516;
    static final int QUERY_VALUE = 255;
    static final int MODE_WRITE_SELECTOR = 1;
    static final int MODE_TRANSACTION_SECOND_VALUE = 128;

    private BrightnessProtocol() {}

    static int dayFromPackedLevels(int packed) { return packed & 0xff; }
    static int nightFromPackedLevels(int packed) { return (packed >>> 8) & 0xff; }
}
