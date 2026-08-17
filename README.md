# TS18 compact status bar

A narrow, reversible Android 10 solution for CB's Topway TS18 that addresses
three separate concerns:

1. **Geometry:** reduce the framework status-bar height from the exact supplied
   OEM 58 dp resource value to **43 dp** (about 75%, and about 41 px at the
   last-observed 153 dpi override).
2. **Input:** restrict collapsed notification-shade interception to one bounded
   top-edge strip so ordinary apps can use the rest of the top edge.
3. **Visuals:** scale collapsed status-bar leaf views to **75%** while keeping
   their layout/touch containers unchanged.

The right-side navigation bar is deliberately not modified by the current
implementation.

## Hard input boundary

The collapsed swipe/drag-down/touch region now has two non-negotiable limits:

- **at least 64 px from either top corner**; and
- **never wider than 20% of the full status-bar/screen width**.

The right navigation inset is also excluded. The 64 px rule is a horizontal
corner exclusion along the top edge: the strip still starts at y=0 so a normal
top-edge downward gesture can begin.

For the exact last-observed 1280×720 geometry with a 55 px right navigation
inset, the default strip is:

```text
screen:       x=0................................................1279
right nav:                                               1225..1279
64px corner exclusion:                                  1216..1279
drag strip (256px):                              960..1215
```

So the effective default region is **x=960..1216** (right coordinate exclusive),
exactly 256 px wide, with 64 px clearance from the physical top-right corner and
9 px clearance from the historical right-nav boundary.

Any configured `touch_fraction` above `0.20` is clamped to `0.20`; any configured
corner gap below `64` is clamped to `64`. If a surface is too small to satisfy
those hard limits, the hook fails open to stock SystemUI rather than silently
weakening them.

## Why two components

The bar height/insets belong to framework resources, while touch ownership and
SystemUI child rendering belong to the SystemUI process. This repository builds:

- **`TS18-StatusBar-Geometry-Magisk-*.zip`** — a systemless product RRO;
- **`TS18-StatusBar-Input-LSPosed-*.apk`** — a narrowly scoped LSPosed module.

Install and validate the geometry module first. Only then install the LSPosed
component. See [`docs/INSTALL.md`](docs/INSTALL.md).

## Default policy

| Setting | Default / hard limit |
|---|---:|
| framework status-bar height | `43dp` |
| shade activation width | `20%` of full screen/window width |
| hard maximum shade width | `20%` |
| top-corner exclusion | `>=64px` |
| right navigation inset | excluded |
| visual leaf scale | `0.75` |
| SystemUI scope | main `com.android.systemui` process only |

## Supplied APK provenance correction

The APK extractor renamed installed files. The previous v0.1.0 source package
incorrectly described the supplied sysbar overlay as merely a sibling and the
active landscape RRO as missing. Per the supplied provenance, this repository now
treats:

- `android.overlay.sysbar_720x1280_10.apk` as the extracted copy corresponding to
  the active `/product/overlay/framework-res_sysbar_rro_1280x720.apk`; and
- `Android System_10.apk` as the renamed extract of `/system/framework/framework-res.apk`.

See [`reference/RENAMED_ASSET_PROVENANCE.md`](reference/RENAMED_ASSET_PROVENANCE.md).
The code still avoids depending on private Topway SystemUI class names because a
filename label is not itself a class/API contract.

## Roadmap

Adding **Previous / Play-Pause / Next** controls to the right navigation strip is
technically plausible without creating another playback authority. It is not in
the current runtime build because the right navigation bar is a protected,
separate SystemUI surface and must not be displaced blindly. The evidence-gated
plan is in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Build

The repository intentionally does not commit a signing private key. CI has two
lanes:

- `build.yml`: compiles/tests debug artefacts for code validation;
- `release.yml`: creates installable stable artefacts when repository signing
  secrets are configured.

Local build prerequisites: JDK 17, Android SDK platform 35/build-tools 35, and
Gradle 8.9. Then:

```bash
gradle --no-daemon clean test :overlay:assembleDebug :lsposed:assembleDebug
ALLOW_DEBUG_SIGNING=1 bash tools/package-release.sh debug
```

Debug artefacts are suitable for first-device development only. Use one stable
keystore before repeated long-term installs; changing the overlay package signer
between builds can make Android reject the replacement.

## Physical status

Source/static validation is not physical TS18 validation. The required staged
acceptance sequence is in [`docs/VALIDATION.md`](docs/VALIDATION.md).
