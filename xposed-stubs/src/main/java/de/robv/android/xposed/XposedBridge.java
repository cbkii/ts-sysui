package de.robv.android.xposed;

public final class XposedBridge {
    private XposedBridge() {}

    public static void log(String text) {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public static void log(Throwable throwable) {
        throw new UnsupportedOperationException("compile-only stub");
    }
}
