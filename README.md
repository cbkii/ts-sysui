# Exact TS18 System UI

A systemless, reversible Android 10/API 29 implementation for CB's exact
Topway TS18 (`s9863a1h10`). It is not a generic TS10/TS18 modification.

The project keeps five authorities separate:

1. **Geometry** — a framework RRO reduces the OEM status-bar height to 43dp.
2. **Visuals** — an exact-SystemUI RRO changes only allow-listed status
   icon/clock dimensions.
3. **Collapsed input** — a SystemUI-scoped LSPosed exact adapter narrows only the
   ordinary collapsed shade touch region.
4. **Right-nav media** — the same LSPosed APK can add a reversible media group to
   the recognised Topway `navbar_left` host and command an existing
   `MediaController`.
5. **Brightness** — an independently gated controller follows the newly supplied
   exact CarSetting binary's selected slider path: Topway 258 for mode/state and
   Android `Settings.System.SCREEN_BRIGHTNESS` for the candidate physical output.

The implementation never replaces `SystemUI.apk`, writes an Android partition,
hooks `system_server`, creates a playback service/session/queue/notification or
takes audio focus.

## Current physical remediation

On the exact unit, the compact top-right drag-down restriction is now physically
confirmed working. The same installed generation produced **no right-sidebar
media controls and no useful brightness behaviour**. Fresh analysis of the
supplied exact `SystemUI.apk` and `CarSetting.apk` then identified two concrete
implementation mismatches:

- the module's navbar preflight used generic IDs (`home`, `back`, `recent_apps`,
  `app`) rather than the exact Topway contract: required `navbar_home`,
  `navbar_back`, `navbar_history`, `navbar_volume_plus`,
  `navbar_volume_reduce`, with optional `navbar_guanping` and `navbar_app`; and
- the newly supplied CarSetting binary's selected slider branch writes
  `screen_brightness=30..255`, while its Topway mode path uses command 258 and
  its 516 path remains useful semantic observation.

An earlier exact-device brightness trace showed Topway 516 activity while the
Android brightness mirror remained unchanged. That evidence is retained rather
than erased: the current `SCREEN_BRIGHTNESS` backend is therefore an
**exact-binary-derived correction that still requires physical confirmation**,
not a claim that the older trace was invalid. The build keeps 516 observation
and detailed readback/Topway diagnostics so the next device test can resolve the
remaining execution-path discrepancy without weakening safety gates.

The current correction is specified in
[`docs/EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md`](docs/EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md).
Repository/CI success remains distinct from physical TS18 qualification.

## Exact binary gates

Behavioural SystemUI mutation requires:

| Field | Required value |
|---|---|
| package | `com.android.systemui` |
| installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Android/API | Android 10 / 29 |
| device token | `s9863a1h10` |
| SystemUI APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| shared UID | `android.uid.systemui` |

Managed brightness additionally requires the exact supplied/current
`CarSetting.apk` contract:

```text
package: com.dofun.carsetting
SHA-256: 06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

Both hashes are checked from the injected SystemUI process off the UI thread.
Changing either protected binary blocks the affected private mutation rather
than broadening compatibility.

## Hard collapsed-input boundary

The physically proven compact path remains unchanged. An armed collapsed shade
strip always:

- remains at least **64 physical pixels** from both top corners;
- is no wider than **20%** of full physical width;
- excludes the current right-navigation inset; and
- keeps stock behaviour for ambiguous coordinate/special SystemUI states.

For the last-observed 1280×720 display and ~55px right nav, the maximum half-open
strip remains **[960,1216)**. Runtime geometry is authoritative.

## Right sidebar media controls

The exact supplied SystemUI owns a vertical weighted `navbar_left`. The corrected
resource contract is:

Required physical controls:

```text
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce
```

Known optional decoded controls:

```text
navbar_guanping
navbar_app
```

The module preserves every OEM View, ID, listener, current order and
`LayoutParams`. Unknown or duplicate direct children remain a STOP. Diagnostics
report the actual live direct-child resource entry names before any attempt to
relax the contract.

The configurable media subset/order remains:

```text
previous,play_pause,next
```

One module-owned weighted group is allowed only when the existing sidebar still
meets >=56dp projected vertical cells and >=48dp existing horizontal width. The
module never widens the strip. Controls remain visible-but-disabled when no
usable session/capability exists. Media authority remains strictly:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

There is no second MediaSession/service, media-key fallback or guessed Topway
media command.

## Exact CarSetting-backed brightness controller

The newly supplied exact CarSetting binary's selected slider path writes:

```text
Settings.System.SCREEN_BRIGHTNESS
raw range: 30..255
```

The module retains logical managed levels **1..10** and maps them linearly:

```text
1=30, 2=55, 3=80, 4=105, 5=130,
6=155, 7=180, 8=205, 9=230, 10=255
```

Logical level 0 remains blocked.

Topway command 258 remains mode authority (`0=Auto`, `1=Day`, `2=Night`). The
exact CarSetting mode transaction is reproduced in order:

```text
write(258, 1, selectedMode)
write(258, 128)
```

The private semantic name of the second stock operation is intentionally not
guessed.

Topway 516 is retained for **semantic observation only by this correction**: it
exposes packed Day/Night slots and the effective Day/Night state. It is not used
as physical-write confirmation. Physical success for this backend requires
writing `SCREEN_BRIGHTNESS`, reading the same setting back to the requested raw
value, **and** observing the intended panel change during exact-device
qualification.

Supported modes remain Auto (stock), Day, Night and Set auto (scheduled). In
stock Auto, Topway continues to decide effective Day/Night; if managed Day/Night
levels are configured, the controller waits for a valid 516 effective-state
observation before choosing which physical level applies. Unknown state fails
open rather than guessing.

Mode and physical confirmation are bounded independently: 258 callback/state for
mode, `SCREEN_BRIGHTNESS` readback for the candidate physical output,
query/read before retry, and at most one controlled retry. Repeated
non-convergence opens only the brightness breaker.

See [`docs/BRIGHTNESS-CONTROLLER.md`](docs/BRIGHTNESS-CONTROLLER.md).

## Diagnostics

The normal dashboard reports, among other state:

- exact SystemUI and brightness compatibility;
- live navbar direct-child names, preflight reason and measurements;
- media-controller selection/action bits;
- Topway 258 mode and 516 observation state;
- physical brightness backend, requested logical/raw level and observed raw
  `screen_brightness`;
- both 258 transaction stage timestamps;
- physical read/write timestamps and convergence result;
- feature-specific breaker/failure state.

The release-derived diagnostic build adds the bounded Diagnostic Console and
structured `TS18SysUI`/LSPosed journal described in
[`docs/DIAGNOSTIC-BUILD-POLICY.md`](docs/DIAGNOSTIC-BUILD-POLICY.md).

## Installation artefacts

Normal packaging produces one Magisk module plus the LSPosed APK:

```text
TS18-SystemUI-Magisk-v<version>-release.zip
TS18-SystemUI-LSPosed-v<version>-release.apk
TS18-SystemUI-Bundle-v<version>-release.zip
SHA256SUMS.txt
```

The combined `ts18_sysui` Magisk ZIP contains both RRO APKs:

```text
system/product/overlay/TS18StatusBarGeometry.apk
system/product/overlay/TS18StatusBarVisuals.apk
```

If either legacy ID `ts18_statusbar_geometry` or `ts18_statusbar_visuals` is
still active, use the bundled migration helper, reboot, then install the combined
ZIP. Install/update the LSPosed APK and scope it **only to the main
`com.android.systemui` process**. Do not add CarSetting, TWCore or Launcher to
scope.

## Build and trust

The APK deliberately uses the legacy Xposed bridge qualified for this API29
stack: `assets/xposed_init`, `IXposedHookLoadPackage`, `XposedBridge` and
`xposedminversion=82`. Local stubs are compile-only and CI verifies they are not
packaged. No signing private key or proprietary APK is committed.

Local prerequisites are JDK 17 and Android SDK platform/build-tools 35. The
normal CI matrix runs repository host contracts, unit tests, debug/release/
diagnostic lint and assembly, APK contracts and packaging.

## Validation status

Compact collapsed-input narrowing is physically confirmed on the exact unit.
The corrected right-nav IDs and CarSetting-backed brightness path remain
**physical validation outstanding** until a new build is installed and tested.
Qualification must include one-command media dispatch, fixed Day/Night physical
readback and visible change, stock Auto, scheduled transitions, stock CarSetting
coexistence, reverse/fullscreen, SystemUI restart, reboot, cold boot and ACC
sleep/wake.

See [`docs/INSTALL.md`](docs/INSTALL.md), [`docs/VALIDATION.md`](docs/VALIDATION.md),
[`docs/RECOVERY.md`](docs/RECOVERY.md) and the exact correction plan.
