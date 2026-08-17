package de.robv.android.xposed;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { throw new UnsupportedOperationException("compile-only stub"); }
        public Throwable getThrowable() { throw new UnsupportedOperationException("compile-only stub"); }
    }
}
