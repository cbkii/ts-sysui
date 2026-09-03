# TS18 brightness controller

## Scope

This feature targets CB's exact current Topway TS18 only. It does not claim
TS10/TS18-family compatibility and it does not replace SystemUI, change panel
files, write backlight sysfs, adjust LCD VCOM/AVDD or alter Factory Backlight
Current calibration.

The controller remains an optional capability of the SystemUI-scoped LSPosed
APK. It introduces no Android service, foreground service, notification, second
scheduler process or `system_server` hook.

## Evidence hierarchy and exact binaries

The retained current target is Android 10/API29, `s9863a1h10`. Exact current
runtime evidence plus the exact active SystemUI implementation outrank an
ambiguous alternative branch found by static decompilation.

| artefact | SHA-256 |
| --- | --- |
| `SystemUI.apk` | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| newly supplied `CarSetting.apk` | `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71` |

The proprietary APKs and decoded output are not stored in the repository. The
repository-safe contracts are under `reference/`.

### Proven active Topway brightness contract

The exact current SystemUI brightness implementation and retained exact-device
interaction trace establish the active 0..10 Day/Night path as:

```text
TWSystemUI.write(516, selector, level)
selector 0 = day slot
selector 1 = night slot
stock level range = 0..10
```

Command 516 callbacks expose:

```text
dayLevel       = packed & 0xff
nightLevel     = (packed >>> 8) & 0xff
effectiveNight = arg1 == 1
```

The current controller deliberately permits managed values **1..10** only.
Level 0 remains blocked until a separate timed no-backlight recovery test is
physically proven.

### Mode transaction corrected from CarSetting

Command 258 remains mode authority:

```text
0 = Auto
1 = Day
2 = Night
```

Fresh CarSetting reverse-engineering added an important transaction detail that
the earlier module omitted. A stock mode change performs both:

```text
write(258, 1, selectedMode)
write(258, 128)
```

The controller now reproduces both operations in that order on the SystemUI main
looper. The private meaning of the second operation is intentionally not guessed.
State queries remain:

```text
write(258, 255)
write(516, 255)
```

### Why `screen_brightness` is diagnostic-only

The newly supplied CarSetting APK also contains a branch that writes
`Settings.System.SCREEN_BRIGHTNESS` over a roughly 30..255 raw range. That static
branch is retained as useful evidence, but it does **not** override the stronger
exact-device runtime result: during the marker-guided current-unit brightness
trace, Android `screen_brightness` stayed unchanged while the stock Topway UI and
516 callbacks changed through the 0..10 domain. The exact current SystemUI path
also uses 516.

Therefore this module does **not** write `SCREEN_BRIGHTNESS`. It samples that
setting only as a diagnostic mirror so a future exact runtime trace can identify
whether a firmware/build-specific alternate path becomes active. Backlight
sysfs, Factory Backlight Current and screen-power control remain separate domains
and are not automatic fallbacks.

## Runtime gates

Brightness mutation requires all of:

1. Android 10/API29 and the exact TS18 device token;
2. exact installed SystemUI SHA-256;
3. exact supplied/current CarSetting SHA-256 while this correction is being
   qualified;
4. the required `TWSystemUI` reflection contract;
5. controller policy explicitly enabled.

Both protected APK hashes are computed off the SystemUI main thread. Unknown or
changed binaries fail open rather than broadening the private contract.

## Modes and levels

User modes remain:

- **Auto (stock)** — Topway mode 0;
- **Day** — Topway mode 1;
- **Night** — Topway mode 2;
- **Set auto (scheduled)** — local-clock policy explicitly selecting Day/Night.

Day and Night levels can remain **preserve current** or be managed from **1..10**.
When levels are managed, the controller first reconciles the corresponding 516
Day/Night slots and confirms them by callback. It then performs any required 258
mode transaction. This means a mode switch selects an already-confirmed safe
slot rather than racing a later level write.

In stock Auto, Topway remains responsible for deciding the effective Day/Night
condition. The module manages only the configured Day/Night slots and does not
rewrite DoFun theme state.

## Runtime architecture

```text
TS18 System UI dashboard
        │ signature-protected package-targeted request
        ▼
SystemUiBridge inside exact SystemUI
        │
        ├── coherent Settings.Global policy transaction
        └── live status snapshot
                    │
                    ▼
             BrightnessController
                    │
        ┌───────────┼──────────────────────────────┐
        │ exact binary/class gates                 │
        │ existing TWSystemUI/TWUtil transport     │
        │ 516 Day/Night writes + callbacks         │
        │ 258 two-stage mode transaction           │
        │ Android screen_brightness mirror: read   │
        │ bounded query/retry + independent break  │
        └───────────┬──────────────────────────────┘
                    ▼
             Topway vendor / MCU
```

The bridge can register while exact identity is still `CHECKING` so diagnostics
can explain a blocked mutation. Mutating requests remain rejected unless the
exact SystemUI identity is `SUPPORTED`.

## Confirmation model

Each required semantic change follows one bounded pending-action sequence:

1. send one exact Topway write;
2. wait up to 1.5s for the corresponding 258 or 516 callback/state predicate;
3. if unconfirmed, issue one bounded 258/516 query;
4. wait a further 1.5s;
5. if still unconfirmed, issue at most one controlled write retry;
6. allow a longer 2.5s final confirmation window;
7. on failure, report `NO_258_CALLBACK` or `NO_516_CALLBACK` and only then record
   one brightness-breaker failure.

The mode write is the stock two-stage transaction `258,1,<mode>` then `258,128`.
The second stage is not treated as an independent user action.

A configuration acknowledgement means **policy saved/consumed**, not physical
success. A 258/516 callback confirms semantic convergence, while visible panel
behaviour remains part of exact-device physical qualification.

## Stale-action protection

Configuration changes increment an action generation immediately. Any mutation
already queued to the SystemUI main looper must still match that generation and
is re-authorised against the current config and live 258/516 state immediately
before the private write. A stale queued or retry action is cancelled rather
than applying an obsolete Day/Night/mode policy.

## Diagnostics

Runtime status includes:

- exact compatibility and hook count;
- CarSetting contract hash used during this qualification build;
- transport readiness;
- current Topway mode;
- 516 callback timestamps, effectiveNight and Day/Night slots;
- exact last observed stock 258/516 write;
- last module action/write time;
- both 258 mode-transaction stage timestamps;
- pending action, attempts and action generation;
- callback confirmation result;
- Android `screen_brightness` value labelled as **diagnostic mirror only**;
- brightness breaker state/failure count.

## Configuration source of truth

Persisted keys remain:

```text
ts18_brightness_policy_version=1
ts18_brightness_enabled=0|1
ts18_brightness_mode=auto|day|night|set_auto
ts18_brightness_day_level=-1|1..10
ts18_brightness_night_level=-1|1..10
ts18_brightness_day_start_minute=0..1439
ts18_brightness_night_start_minute=0..1439
ts18_brightness_debug=0|1
```

`-1` means preserve current. Mutation defaults off.

## Test Day / Test Night

The dashboard test actions require a safe managed non-zero Day/Night value.
Before a test, the previous policy is retained in memory for Restore. The test
must not be called successful merely because the policy was persisted or a
callback arrived; physical panel change must also be observed during device
qualification.

## Safety and rollback

Do not respond to a failed physical test by:

- switching the primary actuator to Android `screen_brightness` merely because a
  static alternate CarSetting branch exists;
- writing backlight sysfs;
- changing Factory Backlight Current;
- hooking CarSetting or `system_server`;
- broadening protected APK hash matching;
- enabling level 0;
- replacing/resigning SystemUI.

If module-generated 258/516 state is callback-confirmed but panel brightness does
not visibly change, STOP and capture a narrow stock-CarSetting/SystemUI-vs-module
marker trace before reconsidering actuator authority.

## Physical validation still required

Before release qualification:

1. verify exact identity/hook/transport state in the dashboard;
2. inspect detected Day/Night 516 values;
3. choose deliberately distinct safe non-zero values;
4. test fixed Day and require 516 level plus 258 mode confirmation and a visible
   physical change;
5. test fixed Night likewise;
6. verify both stages of each required 258 mode transaction;
7. test stock Auto and Set auto in both directions;
8. disable the controller and confirm stock SystemUI/CarSetting ownership is
   restored;
9. exercise headlights/ILL, reverse camera, fullscreen/projection and normal apps;
10. qualify SystemUI restart, warm reboot, cold boot and repeated ACC sleep/wake.
