# Evidence limits and unresolved items

## Established by supplied static/project evidence

- Target firmware family is Android 10/API 29.
- Last-observed physical/base layout is 1280×720 with about 55 px top and 55 px
  right reserved regions and density override 153.
- The exported active sysbar RRO targets `android` and encodes 58 dp for the three
  status-bar height resources; this project overrides only those heights to 43 dp.
- `Android System_10.apk` is the renamed framework resource export, not executable
  SystemUI; `com.android.systemui.plugins_10.apk` is an interface library.
- Historical WindowManager evidence showed the TS18 StatusBar spanning full 1280
  px at physical top-left, but present runtime evidence still outranks that baseline.

## Safety assumptions now enforced in code

The LSPosed input hook no longer assumes historical geometry is still current. It
mutates only when live runtime data shows:

- window-local origin `(0,0)`;
- StatusBar width exactly equals physical display width;
- stock touchable mode is REGION;
- stock region is non-empty, rectangular and full-width;
- region/window heights both match the compact bar within a small tolerance;
- keyguard is not locked.

A heads-up/custom/expanded/offset/partial-width or otherwise ambiguous surface is
left stock. Visual scaling and input mutation are both disabled by default.

## Still uncertain / requires physical evidence

1. Current RRO/idmap policy may reject a non-platform-signed product overlay.
2. Exact current SystemUI collapsed `InternalInsetsInfo` shape is not statically
   proven; the new policy may intentionally fail open until captured.
3. No separate `system_server` transient-bar gesture has been proven or disproven
   on the current firmware. No framework-process hook is included.
4. Current Magisk/Zygisk/LSPosed versions and other SystemUI writers should be
   re-read before installation.
5. Optional 0.75 leaf scaling remains generic/experimental; exact resource-based
   visual sizing needs current SystemUI hierarchy/resources.
6. Right-nav media controls still need exact view/lifecycle evidence.
7. This repository intentionally remains a legacy Xposed bridge module. A modern
   libxposed migration requires the exact installed LSPosed capability to be
   established first.

These gaps do not justify replacing SystemUI, writing partitions, changing global
density/overscan or adding `system_server` hooks pre-emptively.
