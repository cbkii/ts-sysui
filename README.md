# TS18 compact status bar

A narrow, reversible Android 10/API 29 solution for CB's exact Topway TS18. It
keeps three authorities separate:

1. **Geometry** — a systemless framework resource overlay reduces the status-bar
   height from the supplied OEM 58 dp value to **43 dp** (about 75%, and about
   41 px at the last-observed 153 dpi override).
2. **Input** — an LSPosed hook can restrict ordinary collapsed notification-shade
   interception to one bounded top-edge strip so apps can use the rest of the
   top edge.
3. **Visuals** — an optional, separately armed SystemUI transform can scale
   collapsed status-bar leaf views to **75%** while leaving their layout/touch
   containers unchanged.

The right-side navigation bar is deliberately not modified by the current
implementation.

## Hard input boundary

Any armed collapsed swipe/drag-down region has two non-negotiable limits:

- **at least 64 px from either physical top corner**; and
- **never wider than 20% of the full status-bar/screen width**.

The current right-navigation inset is also excluded. The 64 px rule is a
horizontal corner exclusion: the strip still starts at y=0 so a normal top-edge
downward gesture can begin.

For the exact last-observed 1280x720 geometry with a 55 px right-navigation
inset, the 20% policy resolves to:

```text
screen:       x=0................................................1279
right nav:                                               1225..1279
64px corner exclusion:                                  1216..1279
drag strip (256px):                              960..1215
```

So the maximum default geometry is **x=960..1216** (right coordinate exclusive),
256 px wide. A configured `touch_fraction` may be reduced as far as `0.01`; any
request above `0.20` is clamped to `0.20`. A configured corner gap below `64` is
clamped to `64`. If the physical-to-window coordinate mapping or collapsed state
cannot be established safely, the hook leaves stock SystemUI behaviour intact.

## Why two components

The bar height/insets belong to framework resources, while touch ownership and
SystemUI child rendering belong to the SystemUI process. This repository builds:

- **`TS18-StatusBar-Geometry-Magisk-*.zip`** — a systemless product RRO;
- **`TS18-StatusBar-Input-LSPosed-*.apk`** — a narrowly scoped legacy-Xposed
  module supported by LSPosed.

The framework RRO is the **only** status-bar height owner in this project. The
LSPosed module no longer rewrites `WindowManager.LayoutParams.height`.

Install and validate geometry first. Only then install the LSPosed component.
See [`docs/INSTALL.md`](docs/INSTALL.md).

## Runtime defaults and staged arming

The LSPosed APK is intentionally **observation-only on first run**. Its master,
input and visual mutations all default to off. This lets hook installation be
confirmed before a protected SystemUI window is changed.

| Setting | Default / hard limit |
|---|---:|
| framework status-bar height | `43dp` |
| LSPosed master mutation switch | **off** |
| collapsed input mutation | **off** |
| configured shade width | `20%` when armed |
| configurable shade-width range | `1%..20%` |
| hard maximum shade width | `20%` |
| top-corner exclusion | `>=64px` |
| right navigation inset | excluded |
| optional visual scaling | **off** |
| configured visual scale | `0.75` |
| LSPosed scope | main `com.android.systemui` process only |

Use `tools/ts18-statusbar-config.sh` under root to enter observation mode, arm
input, optionally arm visuals, or disarm all runtime mutations. The recommended
sequence is documented in `docs/INSTALL.md` and `docs/VALIDATION.md`.

## LSPosed API compatibility

The current APK intentionally uses the **legacy Xposed bridge contract**:
`assets/xposed_init`, `IXposedHookLoadPackage`, `XposedBridge` and
`xposedminversion=82`. It is not a modern libxposed/API-100 implementation. The
local `xposed-stubs` project is compile-only and CI verifies those bridge classes
are not packaged into the APK. See
[`docs/LSPOSED-COMPATIBILITY.md`](docs/LSPOSED-COMPATIBILITY.md).

## Supplied APK provenance

The extractor used for the supplied firmware artefacts renamed installed files.
Per the supplied provenance this repository treats:

- `android.overlay.sysbar_720x1280_10.apk` as the extracted copy corresponding to
  the active `/product/overlay/framework-res_sysbar_rro_1280x720.apk`; and
- `Android System_10.apk` as the renamed extract of
  `/system/framework/framework-res.apk`.

See [`reference/RENAMED_ASSET_PROVENANCE.md`](reference/RENAMED_ASSET_PROVENANCE.md).
The runtime code still avoids depending on private Topway SystemUI class names
because a filename label is not a class/API contract.

## Roadmap

Adding **Previous / Play-Pause / Next** controls to the right navigation strip is
technically plausible without creating another playback authority. It is not in
the current runtime build because the right navigation bar is a protected,
separate SystemUI surface and must not be displaced blindly. The evidence-gated
plan is in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Build

The canonical project version is in `version.properties`; Gradle APK metadata,
Magisk `module.prop` generation and package filenames all derive from it.

The repository intentionally does not commit a signing private key. CI has two
lanes:

- `build.yml`: host policy checks, unit tests, Android Lint, debug APK assembly,
  APK contract validation and debug packaging;
- `release.yml`: the same relevant checks plus signed release assembly,
  signature verification, exact tag/version matching and release packaging when
  repository signing secrets are configured.

The committed Gradle wrapper pins Gradle 8.9 and its distribution checksum. Local
build prerequisites are JDK 17 and Android SDK platform 35/build-tools 35. Then:

```bash
./gradlew --no-daemon clean test :overlay:lintDebug :lsposed:lintDebug \
  :overlay:assembleDebug :lsposed:assembleDebug
bash tools/test-apk-contract.sh lsposed/build/outputs/apk/debug/lsposed-debug.apk
ALLOW_DEBUG_SIGNING=1 bash tools/package-release.sh debug
```

Debug artefacts are suitable for first-device development only. Use one stable
keystore before repeated long-term installs; changing the overlay package signer
between builds can make Android reject the replacement.

## Physical status

Source/static/CI validation is not physical TS18 validation. The required staged
SystemUI restart, reboot, cold-boot and ACC acceptance sequence is in
[`docs/VALIDATION.md`](docs/VALIDATION.md).
