# Exact APK navbar and brightness correction plan

Status: implementation/finalisation plan for the post-v0.5.7 physical remediation.

## Evidence basis and resolution rule

This work is based on the user's exact privileged APKs plus direct exact-device
runtime evidence. The compact top-right shade restriction is physically proven;
the installed right-sidebar media controls and brightness configuration were not.

Exact supplied hashes used by this remediation include:

- `SystemUI.apk`: `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`
- newly supplied `CarSetting.apk`: `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71`

Proprietary APKs/decoded output are not committed.

A final evidence reconciliation is required because fresh static CarSetting
analysis exposed a `Settings.System.SCREEN_BRIGHTNESS` branch, while the retained
marker-guided current-unit trace and exact current SystemUI implementation show
Topway 516 as the path that actually changes/represents the 0..10 Day/Night
brightness domain. During that trace Android `screen_brightness` remained
unchanged while Topway brightness changed.

Therefore this PR follows the project evidence hierarchy: **current exact-device
runtime plus the exact active SystemUI path outrank an ambiguous alternate static
branch**. The CarSetting branch is retained as diagnostic evidence, not promoted
to primary mutation without runtime proof.

## Confirmed exact findings

### Right sidebar

`com.android.systemui.statusbar.phone.NavigationBarView` and its vertical
`navbar_left` child are the correct ownership/lifecycle surface. Exact SystemUI
resource entry names are:

Required:

- `navbar_home`
- `navbar_back`
- `navbar_history`
- `navbar_volume_plus`
- `navbar_volume_reduce`

Known optional:

- `navbar_guanping`
- `navbar_app`

The failed generation used generic `home`, `back`, `recent_apps` and optional
`app`; exact preflight therefore rejected the live topology before injection.

### Brightness authority

The strongest combined evidence establishes:

```text
Topway 516 = active Day/Night brightness slots and callback authority
selector 0 = Day
selector 1 = Night
managed safe range = 1..10
arg1 == 1 in callback = effective Night
```

State queries remain:

```text
write(258, 255)
write(516, 255)
```

The newly supplied CarSetting APK contains an alternate Android
`screen_brightness` branch (roughly slider 0..225 -> raw 30..255). Because exact
runtime evidence showed that Android setting unchanged while physical/Topway
brightness changed, the module **must not use that branch as ordinary actuator**.
It may observe `screen_brightness` as a diagnostic mirror only.

### Mode transaction

Fresh CarSetting reverse-engineering did reveal a valid missing operation in the
old module. Stock mode selection performs:

1. `write(258, 1, selectedMode)`
2. `write(258, 128)`

The controller reproduces both in order. The private meaning of the second call
is intentionally not guessed.

No evidence requires LSPosed scope outside the main `com.android.systemui`
process.

## Implementation requirements

### A. Correct navbar resource contract

1. Require `navbar_home`, `navbar_back`, `navbar_history`,
   `navbar_volume_plus`, `navbar_volume_reduce`.
2. Treat `navbar_guanping` and `navbar_app` as the only currently known optional
   direct children.
3. Preserve exact `navbar_left` vertical weighted-host admission.
4. Preserve every OEM View/listener/ID/runtime order/visibility/LayoutParams.
5. Keep unknown/duplicate direct children as STOP conditions.
6. Report live direct-child resource entry names in diagnostics.
7. Reject regression to generic resource names in tests.

### B. Restore the proven brightness authority

1. Manage Day/Night slot values through the existing exact SystemUI
   `TWSystemUI.write(516, selector, level)` path.
2. Keep managed values at `1..10`; level 0 remains prohibited.
3. Preserve `-1` as preserve-current independently for Day/Night.
4. Confirm Day/Night writes from the corresponding 516 callback/state predicate.
5. Query 516 before retry and permit at most one controlled retry.
6. On non-convergence report `NO_516_CALLBACK` and use only the brightness breaker.
7. Never substitute `SCREEN_BRIGHTNESS`, backlight sysfs, panel calibration or a
   second actuator automatically.

### C. Correct the stock mode transaction

For each required mode change, on the SystemUI main looper after transport
readiness:

1. `write3(258, 1, mode)`
2. `write2(258, 128)`

Mode confirmation remains the observed 258 callback/state. Non-convergence is
`NO_258_CALLBACK`.

### D. Policy behaviour

- Managed Day/Night 516 slots are converged and callback-confirmed before a mode
  transition selects them.
- Fixed Day selects mode 1 after required managed slots are settled.
- Fixed Night selects mode 2 after required managed slots are settled.
- `set_auto` selects explicit Day/Night from local clock without redefining the
  stock Auto input semantics.
- Stock Auto leaves mode 0 in charge of effective Day/Night and only manages the
  configured 516 slots.
- `screen_brightness` remains an observed mirror, not a success predicate.

### E. Stale-action safety

Every persisted configuration change invalidates queued actions immediately.
Before a main-looper mutation executes, it must still match the current action
generation **and** be the action currently required by fresh policy/258/516 state.
A stale pending/retry action is cancelled rather than applied.

### F. Diagnostics

Expose:

- exact binary/class compatibility;
- Topway transport readiness;
- 258 mode/current callback time;
- 516 Day/Night/effective state and callback time;
- exact last stock 258/516 write;
- last module action/write;
- both mode-transaction stages;
- pending action/attempts/generation;
- callback confirmation/breaker state;
- Android `screen_brightness` clearly labelled diagnostic mirror only;
- live navbar direct-child resource names and exact preflight STOP reason.

Diagnostic output must never describe Android readback as proof of physical
Topway brightness convergence.

### G. Verification

Tests/contracts must cover:

- exact navbar resource names and rejection of old generic names;
- 516 Day and Night callback matching;
- 258 callback matching;
- required two-stage 258 mode transaction;
- no `Settings.System.putInt(...SCREEN_BRIGHTNESS...)` mutation;
- level 0 rejection;
- query-before-retry and bounded retry;
- stale main-looper action invalidation/re-authorisation;
- independent feature breaker behaviour;
- proprietary APK exclusion and exact hash provenance.

Run the complete host/source contracts, JVM tests, debug/release/diagnostic lint
and assembly, APK contracts, packaging and source-manifest integrity without
weakening gates.

## Acceptance boundary

Source/CI success is not physical proof. After merge/release, exact-device
qualification still needs sidebar injection, one-command media dispatch,
fixed Day/Night 516 writes with visible panel change, stock Auto, local schedule,
reverse/fullscreen/nav visibility, SystemUI restart, reboot, cold boot and ACC
sleep/wake. If 258/516 callback state converges but the panel does not, STOP and
collect a narrow marker-assisted stock-vs-module trace before changing authority.
