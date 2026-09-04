# Physical 0.5.1 remediation roadmap

## Status and purpose

This document records the physical failure that initiated the remediation after
installing `0.5.1` on CB's exact Topway TS18 (`s9863a1h10`, Android 10/API 29),
and the staged qualification path that remains required after the later exact-APK
correction.

The first exact-device installation established that:

- compact top-right collapsed-shade routing works physically;
- Previous / Play-Pause / Next were absent from the right navigation strip;
- Day/Night selection produced no useful observable brightness change; and
- the stock sidebar retained Home, Recents, Back, Volume+ and Volume−.

Subsequent exact supplied `SystemUI.apk` and `CarSetting.apk` analysis superseded
this roadmap's earlier actuator hypotheses. The controlling correction contract
is now `EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md`.

## Current exact correction

### Right navigation

The correct direct-child resource entry names are:

Required:

```text
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce
```

Known optional:

```text
navbar_guanping
navbar_app
```

The earlier generic `home`, `back`, `recent_apps` and `app` assumptions caused
safe preflight to reject the exact host before injection.

### Brightness

The exact supplied CarSetting build's active physical slider path writes:

```text
Settings.System.SCREEN_BRIGHTNESS
raw range 30..255
```

Managed logical levels remain 1..10 and map linearly to that raw range. Level 0
remains blocked.

Topway command/callback 258 remains mode authority:

```text
0 = Auto
1 = Day
2 = Night
```

A mode write reproduces the exact observed stock transaction:

```text
write(258, 1, selectedMode)
write(258, 128)
```

Topway 516 remains useful **observation only** for packed Day/Night slots and the
effective Day/Night state. It is not the normal physical actuator and is not
physical convergence proof.

Managed brightness additionally requires the exact supplied CarSetting contract:

```text
package: com.dofun.carsetting
SHA-256: 06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

## Non-negotiable safety boundary

Preserve:

- LSPosed scope restricted to the main `com.android.systemui` process;
- exact installed SystemUI identity gating before private mutation;
- exact CarSetting hash gating before the CarSetting-derived physical backend is
  used;
- systemless RROs and no direct `/system` or `/product` writes;
- framework geometry as the sole status-bar-height authority;
- existing `MediaSessionManager -> MediaController -> TransportControls` media
  authority;
- Topway 258 as semantic mode authority and 516 as observation-only state;
- `Settings.System.SCREEN_BRIGHTNESS` as the exact CarSetting-backed physical
  output for this build only;
- no additional MediaSession, playback service, queue, notification or
  audio-focus owner;
- no `system_server` hooks;
- independent compact, navbar and brightness failure domains;
- fail-open behaviour for unknown identity/topology/state; and
- managed brightness level 0 blocked until separate timed recovery qualification.

Do not weaken exact-device hash/topology safety to make a feature appear active.

## Implemented remediation milestones

### R0 — live observability and control bridge

The signature-protected package-targeted in-SystemUI bridge supports coherent
feature configuration, read-only runtime status and private `ResultReceiver`
acknowledgements. Policy acknowledgement is never reported as physical success.

### R1 — TS18 System UI dashboard

The launcher Activity provides compact, right-nav and brightness configuration
with bounded diagnostics. Root scripts remain recovery/development tools.

### R2 — right-nav activation and exact preflight

OFF -> ON configuration invalidates the nav cache and requests immediate
reconciliation. Preflight reports live topology/measurements and accepts only the
exact mandatory/optional resource contract above, with unknown/duplicate direct
children remaining a STOP.

The module uses the existing OEM strip width, requires >=56dp projected vertical
cells and >=48dp horizontal width, and never widens the sidebar merely to pass a
dp target. Enabled media controls remain visible but disabled when no usable
MediaController/capability exists.

### R3 — media diagnostics

Diagnostics report controller count, selected package, playback state/action
bits and injected actions. Media dispatch remains exactly one supported
`TransportControls` operation per accepted tap.

### R4 — brightness state and physical confirmation

The brightness controller now separates:

```text
exact SystemUI + CarSetting compatibility
Topway transport readiness
258 mode state/confirmation
516 effective-state observation
requested logical/raw physical brightness
SCREEN_BRIGHTNESS write/readback
brightness-only breaker
```

Physical success requires the raw `SCREEN_BRIGHTNESS` readback to equal the
requested raw value. Missing mode confirmation is reported separately from a
physical readback mismatch.

### R5 — bounded pending-action model

One required semantic/physical action is pending at a time. After the first
write the controller waits, performs one matching state query/read before retry,
permits at most one controlled retry, and only then records a breaker failure.

Relevant failure states include:

```text
NO_258_CALLBACK
SCREEN_BRIGHTNESS_READBACK_MISMATCH
AUTO_EFFECTIVE_STATE_UNKNOWN
```

A missing 516 observation is not a failed physical write. In stock Auto with
managed levels it blocks selection of a Day/Night target until effective state is
known, then fails open rather than guessing.

### R6 — single Magisk module

The independently built geometry and visual RRO APKs are packaged in one normal
user-facing `ts18_sysui` Magisk ZIP. Legacy split IDs are migrated only through
the bounded helper; no unknown `/data/adb` state is deleted.

### R7 — tests, CI and package contracts

Repository checks cover exact navbar IDs/topology, mapping/readback policy,
258 transaction constants, 516 observation-only constraints, stock-Auto safety,
combined packaging, exact binary fixtures, Android debug/release/diagnostic
builds and source integrity. These checks are source/CI evidence only.

## Physical qualification after implementation

1. Install the combined Magisk module and reboot.
2. Install/update the LSPosed APK and scope only `com.android.systemui`.
3. Reboot and open **TS18 System UI**.
4. Confirm exact SystemUI and CarSetting compatibility are READY.
5. Confirm the nav host and exact direct-child preflight state.
6. Enable Play/Pause only; it must appear even with no usable media session while
   the stock panel itself is visible.
7. Start media and verify exactly one command per tap, then add Previous/Next.
8. Confirm Home, Back, Recents, Volume+ and Volume− remain unchanged.
9. Configure safe distinct logical Day/Night values from 1..10.
10. Test fixed Day: require 258 mode confirmation, raw `SCREEN_BRIGHTNESS`
    readback convergence and a corresponding physical brightness change.
11. Repeat for fixed Night with a distinct value.
12. Test stock Auto with managed levels; require a known 516 effective state
    before the matching physical level is applied.
13. Test scheduled Set-auto transitions in both directions.
14. Test stock CarSetting/SystemUI brightness coexistence when module brightness
    is disabled and enabled as designed.
15. Qualify reverse/fullscreen, SystemUI restart, reboot, cold boot, ACC
    sleep/wake, calls, projection and representative long-duration use.

## STOP conditions

STOP and leave only the affected layer stock/off if:

- exact SystemUI identity differs;
- exact CarSetting identity differs for managed brightness;
- a required private hook/class contract is absent;
- navbar topology cannot be interpreted unambiguously;
- any OEM nav function disappears or changes semantics;
- an injected cell falls below the accepted physical floor;
- SystemUI becomes unstable;
- Topway 258 mode cannot be confirmed within the bounded sequence;
- `SCREEN_BRIGHTNESS` is rejected or cannot be read back to the requested raw
  value;
- stock Auto effective Day/Night state is unknown while a managed level would
  otherwise be selected; or
- reverse/vehicle behaviour is being fought.

Do not respond to a STOP by writing backlight sysfs, factory panel calibration,
screen-power commands, broadening LSPosed scope, weakening hashes/topology, or
inventing private vendor semantics.

## Completion boundary

A review-ready PR may be described as source/CI validated after all repository
checks and review findings pass. It must not be called physically qualified until
the exact-unit progression above and `VALIDATION.md` are completed.