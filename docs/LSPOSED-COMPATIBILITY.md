# LSPosed / Xposed compatibility

This APK is intentionally a **legacy Xposed-bridge module** for the exact Android
10/API 29 TS18 target. It is not a modern libxposed/API-100 module.

Runtime contract:

- entry point: `assets/xposed_init`;
- entry interface: `IXposedHookLoadPackage`;
- declared minimum legacy bridge version: `82`;
- LSPosed scope: `com.android.systemui` main process only;
- local `xposed-stubs` module: compile-only, never packaged into the APK;
- hook registration uses legacy `XposedHelpers.findAndHookMethod` with exact
  Android 10 method signatures instead of broad method-name hooks.

The local stub surface is documented in `xposed-stubs/API_CONTRACT.md` and checked
by `tools/test-xposed-stubs.sh`. CI also inspects the assembled APK to ensure
`de.robv.android.xposed.*` classes were not accidentally embedded.

Do not migrate this module merely to claim a newer API number. A modern libxposed
migration changes the entry metadata and hook API and must first be qualified
against the exact LSPosed build installed on the TS18. Do not mix legacy and
modern entry mechanisms in one APK.
