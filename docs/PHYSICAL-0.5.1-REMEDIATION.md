# Physical 0.5.1 remediation roadmap

## Purpose

This roadmap records the first exact-device remediation milestone after physical installation of `0.5.1` on CB's Topway TS18 (`s9863a1h10`, Android 10/API 29).

Current physical findings are higher-value evidence than CI/static assumptions:

- all `0.5.1` release assets were installed;
- the LSPosed module is scoped only to the main `com.android.systemui` process, which remains the intended scope;
- applying Day versus Night through the brightness UI produced no observable brightness change;
- Previous / Play-Pause / Next controls were not visible in the right-side navigation strip;
- the stock right strip visibly retains Home, Recents, Back, Volume+ and Volume-;
- separate geometry and visual Magisk modules are an unnecessary user-facing installation burden and should become one normal module.

The goal is not to broaden privilege or replace SystemUI. The goal is to make activation, runtime matching and confirmation observable, then repair only the exact gates proven to be blocking the required behaviour.

## Non-negotiable safety boundary

Preserve:

- LSPosed scope restricted to the main `com.android.systemui` process;
- exact installed SystemUI identity gating before private mutation;
- systemless RROs; no direct `/system` or `/product` writes;
- framework geometry as the sole status-bar-height authority;
- existing `MediaSessionManager -> MediaController -> TransportControls` media authority;
- recovered Topway 258/516 brightness semantic authority;
- no additional MediaSession, playback service, queue, notification or audio-focus owner;
- no `system_server` hooks;
- independent compact, navbar and brightness failure domains;
- fail-open behaviour for unknown identity/topology/state;
- managed brightness level `0` blocked until separate timed recovery qualification.

Do not weaken exact-device hash/topology safety simply to make the feature appear active.

## Root-cause hypotheses to discriminate

### Right nav

The current implementation can remain visually stock because:

1. navbar mutation defaults off and normal installation does not arm it;
2. configuration changes are not guaranteed to trigger immediate in-process reconciliation from the default-off state;
3. the exact preflight may reject the live host/topology/weights/width/height;
4. the horizontal `56dp` production requirement may reject a stock-width sidebar that is physically around the OEM 55px interaction width;
5. media-session state should not be capable of hiding the group, so complete absence of buttons points earlier in the activation/preflight path.

### Brightness

The current implementation can appear to apply configuration while producing no physical change because:

1. brightness mutation defaults off;
2. Day and Night levels can both remain `preserve current`, so mode switching may be visually indistinguishable;
3. the configuration acknowledgement proves persistence, not transport readiness or hardware convergence;
4. the controller waits for mode/level state callbacks before acting;
5. the 450ms confirmation loop can be too aggressive for an OEM MCU path and can open the brightness-only breaker before delayed confirmation;
6. a private Topway contract mismatch or missing callback currently fails open with insufficient user-visible explanation.

## Milestone sequence

### R0 - live observability and control bridge

Generalise the current brightness-only SystemUI configuration bridge into one signature-protected, package-targeted SystemUI bridge that supports:

- coherent feature configuration transactions;
- read-only runtime status snapshots;
- private `ResultReceiver` acknowledgements;
- no manifest receiver or new privileged app permission.

Expose enough state for the user to answer why a feature is active, off, blocked or failed without logcat.

### R1 - TS18 System UI dashboard

Replace the brightness-only launcher UX with a coherent dashboard containing:

- installation/identity state;
- compact-status controls/status;
- right-nav controls/status;
- brightness controls/status;
- bounded diagnostics export/status text.

Shell helpers remain recovery/development tools, not the normal product UX.

### R2 - right-nav activation and preflight remediation

- make OFF -> ON configuration reconcile immediately without SystemUI restart;
- invalidate nav configuration cache after a successful transaction;
- expose exact preflight STOP reason and measured host geometry;
- retain exact topology safety, but distinguish known `GONE`/optional stock controls from unknown children when exact evidence supports it;
- use full stock sidebar width for module controls;
- maintain >=56dp vertical production target, >=48dp absolute horizontal floor, and prefer >=56dp horizontally where the existing OEM strip permits it;
- never widen the sidebar just to satisfy dp rounding;
- keep the media group visible but disabled when no session exists.

### R3 - media diagnostics

Keep the existing public media backend and add status for:

- discovered controller count;
- selected package;
- playback state and action bits;
- per-button enabled/disabled reason.

Physical progression remains Play/Pause first, then Previous/Next.

### R4 - brightness state machine and confirmation

Replace persistence-only success semantics with explicit runtime states:

`HOOK_INSTALLED -> IDENTITY_VERIFIED -> TRANSPORT_READY -> MODE_STATE_KNOWN -> LEVEL_STATE_KNOWN -> CONFIG_ENABLED -> ACTION_PENDING -> CALLBACK_CONFIRMED -> ACTIVE/SETTLED`

with explicit `BLOCKED(reason)` / `ERROR(reason)` states.

Track callback/write timestamps and expose current Topway mode, effective night state and stored Day/Night levels.

### R5 - brightness UX and bounded pending-action model

- show detected current Day/Night slot values;
- warn when Day and Night values are equal;
- distinguish `policy saved` from `hardware confirmed`;
- add bounded Test Day/Test Night flows with safe non-zero values and restore guidance;
- replace the fixed 450ms repeated-write loop with one pending action, callback-based confirmation, one bounded semantic query on timeout, controlled backoff and a small retry budget;
- do not open the breaker on one ordinary slow confirmation window.

### R6 - single Magisk module

Keep the two Android RRO projects independently buildable/testable but package both APKs into one user-facing Magisk module:

`TS18-SystemUI-Magisk-v<version>-release.zip`

containing:

- `TS18StatusBarGeometry.apk`;
- `TS18StatusBarVisuals.apk`.

The installer must validate API/device/payloads and guard against duplicate legacy module IDs (`ts18_statusbar_geometry`, `ts18_statusbar_visuals`). Do not delete unknown `/data/adb` state blindly.

### R7 - tests, CI and release packaging

Add coverage for:

- OFF -> ON nav reconciliation without process restart;
- config cache invalidation;
- exact known topology variants and unknown-child STOP;
- stock-width horizontal policy, 48dp floor and >=56dp vertical production target;
- visible-disabled no-session buttons;
- exactly-once reinflation;
- secure status/config bridge;
- `policy-saved != hardware-confirmed`;
- equal Day/Night warning;
- transport/callback missing and delayed-confirmation paths;
- bounded unconfirmed-write failure;
- subsystem breaker isolation;
- one combined Magisk ZIP containing exactly both RRO APKs;
- legacy-module migration guard.

Do not weaken existing identity, overlay allow-list, media exactly-once, signing, source-manifest or proprietary-artifact checks.

## User-visible runtime status contract

At minimum expose:

- exact SystemUI identity state/detail/hash;
- compact hook and block state;
- nav hook/root/host/preflight state and reason;
- nav measured width/height/density and projected cell size;
- stock child IDs/visibility/order/weights;
- injected actions and nav breaker state;
- active-session count, selected media package/state/actions;
- brightness hook compatibility, transport readiness, mode/levels-known state;
- current Topway mode/effectiveNight/Day/Night levels;
- last 258/516 callbacks, last stock/module write, pending action and confirmation result;
- brightness breaker state.

Avoid unrelated user/media data.

## Physical qualification after implementation

1. Install the single combined Magisk module and reboot.
2. Install/update the LSPosed APK and scope only `com.android.systemui`.
3. Reboot and open TS18 System UI diagnostics.
4. Confirm exact identity/hooks are READY.
5. Confirm nav host/preflight state.
6. Enable Play/Pause only; button must remain visible even without a session.
7. Start media and verify one command per tap, then add Previous/Next.
8. Confirm all OEM nav controls remain present/correct.
9. Inspect detected Day/Night levels; configure deliberately distinct safe non-zero values.
10. Test fixed Day then Night and require 258/516 confirmation.
11. Test scheduled transition.
12. Qualify SystemUI restart, reboot, cold boot, ACC sleep/wake, reverse camera, calls and projection.

## STOP conditions

STOP and leave the affected layer stock if:

- exact SystemUI identity differs;
- required private hook/class contract is absent;
- navbar topology cannot be interpreted unambiguously;
- any OEM nav function disappears or changes semantics;
- an injected cell falls below the accepted physical floor;
- SystemUI becomes unstable;
- Topway brightness writes cannot obtain semantic confirmation;
- confirmed module 258/516 writes still produce no physical brightness change.

For the final brightness case, collect a narrow stock-vs-module marker trace and reassess the exact actuator from that evidence rather than adding generic Android/sysfs fallbacks.

## Completion criteria

A review-ready PR must include the roadmap, implementation, tests, CI, updated install/recovery/validation docs and packaging changes. It may be described as source/CI validated only. Do not publish a release or claim physical TS18 success until the exact-unit matrix above is performed.
