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
5. **Brightness** — an independently gated controller uses the exact current
   SystemUI Topway 516 Day/Night brightness path and the corrected two-stage 258
   mode transaction.

The implementation never replaces `SystemUI.apk`, writes an Android partition,
hooks `system_server`, creates a playback service/session/queue/notification or
takes audio focus.

## Current physical remediation

On the exact unit, the compact top-right drag-down restriction is physically
confirmed working. The same installed generation produced **no right-sidebar
media controls and no useful brightness behaviour**.

The exact APK/runtime review identified two concrete causes/corrections:

- sidebar preflight used generic IDs (`home`, `back`, `recent_apps`, optional
  `app`) rather than the exact Topway IDs: required `navbar_home`,
  `navbar_back`, `navbar_history`, `navbar_volume_plus`,
  `navbar_volume_reduce`, with optional `navbar_guanping` and `navbar_app`;
- the earlier brightness implementation omitted the second operation in the
  stock CarSetting mode transaction. The corrected sequence is
  `write(258,1,<mode>)` then `write(258,128)`.

Fresh static analysis of the newly supplied CarSetting APK also exposed an
alternate branch that writes Android `screen_brightness`. It is **not promoted
to the primary actuator** because stronger exact-device evidence shows the
current stock SystemUI/physical interaction using Topway 516 while Android
`screen_brightness` remained unchanged. The module observes the Android value
for diagnostics only.

The governing reconciliation is documented in
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

During qualification, managed brightness additionally verifies the supplied
CarSetting contract:

```text
package: com.dofun.carsetting
SHA-256: 06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

Hashes are computed from the injected SystemUI process off the UI thread.
Unknown/changed protected binaries fail open rather than broadening a private
contract.

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
report actual live direct-child resource entry names before any compatibility
change is considered.

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

## Exact TS18 brightness controller

The strongest exact evidence establishes Topway command/callback **516** as the
active Day/Night brightness authority:

```text
write(516, 0, dayLevel)
write(516, 1, nightLevel)
managed level range: 1..10
```

The callback provides both Day/Night slots and the current effective condition.
Managed level 0 remains blocked.

Topway command 258 remains mode authority (`0=Auto`, `1=Day`, `2=Night`). The
fresh CarSetting analysis corrected the mode transaction to:

```text
write(258, 1, selectedMode)
write(258, 128)
```

The private semantic name of the second stock operation is intentionally not
guessed.

The module queries both authorities through the existing SystemUI transport:

```text
write(258, 255)
write(516, 255)
```

Managed 516 slot writes and mode changes are confirmed by the corresponding
callbacks. Each action gets one initial write, one query before retry, at most
one controlled retry and an independent brightness breaker on repeated
non-convergence.

Android `Settings.System.SCREEN_BRIGHTNESS` is sampled and reported only as a
**diagnostic mirror**. It is not written by this controller and does not define
Topway brightness success. Backlight sysfs, Factory Backlight Current, theme
state and screen power likewise remain separate domains.

Queued mutations are generation-gated and re-authorised against current policy
and live 258/516 state immediately before the main-looper private call, so a fast
configuration change cannot apply a stale queued Day/Night/mode action.

See [`docs/BRIGHTNESS-CONTROLLER.md`](docs/BRIGHTNESS-CONTROLLER.md).

## Diagnostics

The normal dashboard reports, among other state:

- exact SystemUI/brightness compatibility;
- live navbar direct-child names, preflight reason and measurements;
- media-controller selection/action bits;
- Topway 258 mode and 516 Day/Night/effective state;
- both 258 transaction-stage timestamps;
- last observed stock and module Topway writes;
- pending brightness action/attempts/generation and callback confirmation;
- Android `screen_brightness` explicitly as a diagnostic mirror;
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
The corrected exact navbar IDs, 516 slot writes and two-stage 258 mode
transaction remain **physical validation outstanding** until a new build is
installed and tested. Qualification must include one-command media dispatch,
fixed Day/Night visible brightness change plus 258/516 confirmation, stock Auto,
scheduled transitions, stock SystemUI/CarSetting coexistence, reverse/fullscreen,
SystemUI restart, reboot, cold boot and ACC sleep/wake.

See [`docs/INSTALL.md`](docs/INSTALL.md), [`docs/VALIDATION.md`](docs/VALIDATION.md),
[`docs/RECOVERY.md`](docs/RECOVERY.md) and the exact correction plan.
