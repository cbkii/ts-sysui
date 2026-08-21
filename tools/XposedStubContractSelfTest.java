import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedStubContractSelfTest {
    private XposedStubContractSelfTest() {}

    private static void yes(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Method load = IXposedHookLoadPackage.class.getMethod(
                "handleLoadPackage", XC_LoadPackage.LoadPackageParam.class);
        yes(load.getReturnType() == void.class, "load-package return type");

        Field packageName = XC_LoadPackage.LoadPackageParam.class.getField("packageName");
        Field processName = XC_LoadPackage.LoadPackageParam.class.getField("processName");
        Field classLoader = XC_LoadPackage.LoadPackageParam.class.getField("classLoader");
        yes(packageName.getType() == String.class, "packageName type");
        yes(processName.getType() == String.class, "processName type");
        yes(classLoader.getType() == ClassLoader.class, "classLoader type");

        Method exactHook = XposedHelpers.class.getMethod(
                "findAndHookMethod", Class.class, String.class, Object[].class);
        yes(exactHook.getReturnType() == XC_MethodHook.Unhook.class,
                "findAndHookMethod return type");
        Method constructorHook = XposedHelpers.class.getMethod(
                "findAndHookConstructor", Class.class, Object[].class);
        yes(constructorHook.getReturnType() == XC_MethodHook.Unhook.class,
                "findAndHookConstructor return type");
        yes(XposedBridge.class.getMethod("log", String.class).getReturnType() == void.class,
                "log(String)");
        yes(XposedBridge.class.getMethod("log", Throwable.class).getReturnType() == void.class,
                "log(Throwable)");

        Method throwable = XC_MethodHook.MethodHookParam.class.getMethod("getThrowable");
        yes(throwable.getReturnType() == Throwable.class, "getThrowable return type");
        Method unhook = XC_MethodHook.Unhook.class.getMethod("unhook");
        yes(unhook.getReturnType() == void.class && Modifier.isPublic(unhook.getModifiers()),
                "Unhook.unhook");

        System.out.println("SUCCESS: legacy Xposed compile-stub contract");
    }
}
