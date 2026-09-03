# Exact APK navbar and brightness correction plan

Status: implementation plan for the post-v0.5.7 physical remediation.

## Evidence basis

This plan is based on the user's exact installed privileged APKs and the current physical observation that the compact top-right shade restriction works while the right-sidebar media controls and brightness settings have no effect.

Exact supplied APK hashes:

- `SystemUI.apk`: `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`
- `CarSetting.apk`: `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71`
- `TWCore.apk`: `40ff14b4fbe88166286b5840ac39d9993e8231ce3af53f984a7e3a2b4a238d9d`
- `Launcher2.apk`: `1308c2a582431afe23290adaf3e80080d7367745d7f4d7b70d86f4371f6f9d20`

The proprietary APKs and decoded output must not be committed.

## Confirmed exact-binary findings

### Right sidebar

`com.android.systemui.statusbar.phone.NavigationBarView` and its vertical `navbar_left` child remain the correct ownership/lifecycle surface. The exact SystemUI resource entry names are:

Required physical controls:

- `navbar_home`
- `navbar_back`
- `navbar_history`
- `navbar_volume_plus`
- `navbar_volume_reduce`

Known optional decoded controls:

- `navbar_guanping`
- `navbar_app`

The current module instead expects `home`, `back`, `recent_apps`, and optional `app`. That exact preflight therefore rejects the live topology before media-control injection.

### Brightness

In the supplied `CarSetting.apk`, the active brightness-slider path is selected by its current build/hardware gate and writes Android `Settings.System.SCREEN_BRIGHTNESS`. The active UI path maps approximately from slider `0..225` to raw `screen_brightness=30..255`.

The alternative CarSetting code path contains Topway command `516`, but that branch is not the primary physical-brightness actuator for this exact build. Command/callback `516` remains useful observation for Topway state, including effective Day/Night information; it must not be used as proof that a physical brightness write converged.

The exact CarSetting mode setter performs this Topway transaction:

1. `write(258, 1, selectedMode)`
2. `write(258, 128)`

The second call is part of the observed stock transaction. Its private semantic meaning is not asserted beyond that evidence.

No supplied evidence requires LSPosed scope outside the main `com.android.systemui` process.

## Implementation requirements

### A. Correct navbar resource contract

1. Replace the incorrect required names with `navbar_home`, `navbar_back`, `navbar_history`, `navbar_volume_plus`, and `navbar_volume_reduce`.
2. Replace optional `app` with `navbar_app`; retain `navbar_guanping` as optional.
3. Preserve exact `navbar_left` vertical weighted-host admission.
4. Preserve every OEM View, listener, ID, current runtime order, visibility, and LayoutParams.
5. Keep unknown direct children as STOP conditions.
6. Improve diagnostics to report live direct-child resource entry names so future firmware drift is unambiguous.
7. Add source/JVM regression coverage that rejects the old resource-name contract.

### B. Correct physical brightness authority

1. Do not use a Topway `516` write as the ordinary physical brightness actuator.
2. Retain the user-facing managed logical range `1..10`; keep `0` unsupported.
3. Map logical levels deterministically to the exact CarSetting raw range `30..255`, with 1 -> 30 and 10 -> 255 and a monotonic linear mapping for intermediate values.
4. Write physical output via `Settings.System.SCREEN_BRIGHTNESS` from the already exact-SystemUI-gated injected process.
5. Read the same setting back and require convergence before reporting physical success.
6. Keep bounded retry and a brightness-only breaker. Never let brightness failure disable compact touch or nav.
7. Preserve `-1` as preserve-current for Day or Night logical levels.
8. Retain Topway 258/516 callbacks as semantic observation only where useful.

### C. Reproduce the stock mode transaction

For each Topway mode change, issue the exact observed sequence on the main looper after transport readiness:

1. `write3(258, 1, mode)`
2. `write2(258, 128)`

Both operations are required transaction stages. Do not invent an unsupported name for the second operation. Mode confirmation continues to use the observed 258 callback/state.

### D. Policy behaviour

- Fixed Day: select Topway Day mode and apply configured Day logical level through `SCREEN_BRIGHTNESS` when managed.
- Fixed Night: select Topway Night mode and apply configured Night logical level when managed.
- Local `set_auto`: select Day/Night by local clock and apply the corresponding managed physical level.
- Stock Auto: retain stock Topway Auto decision. If managed Day/Night levels are configured, apply the corresponding physical level only when the effective Day/Night observation is known; otherwise fail open rather than guessing.

### E. Diagnostics

Expose semantic and physical state separately:

- Topway mode known/current;
- 516 effective-night state and observed packed Day/Night slots, labelled observation-only;
- requested logical brightness level;
- requested raw `screen_brightness`;
- observed raw `screen_brightness`;
- last physical Settings write/readback;
- physical convergence result;
- 258 transaction first/second stage status;
- brightness breaker and failure cause;
- navbar direct-child resource-name summary and exact preflight STOP reason.

Diagnostic output must distinguish transport failure, mode non-confirmation, Settings write rejection, raw readback mismatch, unknown stock-Auto effective state, and navbar topology rejection.

### F. Repository policy and documentation

Update repository policy and docs that currently identify command 516 as the physical brightness authority or prohibit `screen_brightness` based on superseded evidence. Keep the exact SystemUI SHA mutation gate unchanged. Record the supplied CarSetting hash as the static evidence source for this backend without committing proprietary binaries.

### G. Verification

Add focused tests for:

- exact navbar resource names;
- logical 1..10 to raw 30..255 mapping boundaries and representative intermediate values;
- raw readback confirmation;
- mode transaction includes both observed 258 stages;
- stock Auto managed levels require known effective state;
- no ordinary `write3(...516...)` physical brightness mutation remains;
- 516 remains observation-only;
- logical level 0 remains invalid.

Then run the complete existing host/source contract suite, JVM tests, debug/release/diagnostic lint and assembly, APK contracts, packaging, and source-manifest integrity. Do not weaken gates to obtain green CI.

## Acceptance boundary

Source/CI success is not physical proof. The corrected build must still be installed on the exact TS18 and validated for sidebar injection, one-command media dispatch, fixed Day/Night brightness, stock Auto behaviour, local scheduled mode, reverse/fullscreen/nav visibility, reboot, cold boot, and ACC sleep/wake before being described as physically proven.
