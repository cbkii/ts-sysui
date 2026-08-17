# Evidence limits and unresolved items

## Established by the supplied static set/project source

- The target firmware family is Android 10/API 29.
- Last-observed physical/base layout is 1280×720 with about 55 px top and 55 px
  right reserved regions, and density override 153.
- The APK exporter renamed installed APKs; exported names are not necessarily
  partition basenames.
- Per the supplied provenance, `android.overlay.sysbar_720x1280_10.apk` is the
  extracted active sysbar RRO associated with
  `/product/overlay/framework-res_sysbar_rro_1280x720.apk` on the device.
- That exact supplied RRO is resource-only, targets package `android`, and
  encodes 58 dp for the three status-bar height resources.
- `Android System_10.apk` is the renamed framework-resource export (`/system/framework/framework-res.apk`): package `android`, resources/manifest, no `classes.dex`. It is not a SystemUI implementation.
- `com.android.systemui.plugins_10.apk` is an interface library, not the actual
  `com.android.systemui` implementation.

## Corrected from v0.1.0

v0.1.0 incorrectly called the exported sysbar APK a sibling and said the active
1280×720 RRO had not been supplied. That conclusion treated the extractor's
filename as the original partition filename. The supplied provenance resolves
that: the active RRO is available under a renamed export filename.

The repository also no longer states that the user failed to supply the
SystemUI artefact. The narrower remaining issue is **binary identity mapping**:
the accessible sixteen-APK manifest does not contain an APK whose decoded
package is `com.android.systemui`. Therefore the repo will not mislabel
`com.android.systemui.plugins_10.apk` as the full runtime implementation. If the
separately supplied SystemUI export is added to this repository's reference
manifest later, record its exported filename, original path and SHA-256 before
using private classes.

## Still uncertain

1. The current device's RRO/idmap policy may reject a non-platform-signed overlay
   despite systemless product placement. This must be validated after first boot.
2. The current collapsed `InternalInsetsInfo` behaviour on this exact SystemUI is
   not statically proven. The hook is deliberately framework-surface based and
   fail-open.
3. It is not yet physically proven that no separate `system_server` transient-bar
   gesture still reacts outside the bounded strip. No `system_server` hook is
   included until contrary evidence exists.
4. Current Magisk/Zygisk/LSPosed versions/module writers were not re-read for this
   source build. Inspect conflicts before installation.
5. The desired interpretation of 75% visual scaling is implemented uniformly in
   X/Y for leaf views. If only vertical size should shrink while horizontal icon
   width/spacing stays stock, change that policy after physical inspection.
6. Adding controls to the right navigation strip requires exact view-hierarchy
   and lifecycle evidence so existing Back/Home/Recents/vehicle affordances are
   not displaced or duplicated.

These gaps do not justify replacing SystemUI, writing partitions, changing global
density/overscan, or hooking system_server pre-emptively.
