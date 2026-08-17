# Legacy Xposed bridge compile contract

This module deliberately uses the **legacy Xposed bridge API**, not the modern
libxposed API. The APK declares `xposedminversion=82` and is loaded from
`assets/xposed_init` by LSPosed's legacy compatibility layer.

The local stubs exist only to compile the narrow API surface used by this
repository. They are `compileOnly` and must never be packaged into the LSPosed
APK. The contract exercised by `tools/test-xposed-stubs.sh` is:

- `IXposedHookLoadPackage.handleLoadPackage(XC_LoadPackage.LoadPackageParam)`;
- `XC_LoadPackage.LoadPackageParam.packageName/processName/classLoader`;
- `XposedBridge.hookAllMethods(Class, String, XC_MethodHook)` returning `Set<XC_MethodHook.Unhook>`;
- `XC_MethodHook.MethodHookParam.thisObject/args/getThrowable()`;
- `XC_MethodHook.Unhook.unhook()`;
- `XposedBridge.log(String)` and `XposedBridge.log(Throwable)`.

Do not expand these stubs speculatively. If runtime LSPosed changes, verify the actual installed bridge first.
