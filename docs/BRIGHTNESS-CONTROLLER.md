# TS18 brightness controller

## Scope

This feature targets CB's exact current Topway TS18 Android 10/API29 unit. It
remains an optional capability of the SystemUI-scoped LSPosed APK. It does not
replace SystemUI, hook `system_server`, alter panel files, touch backlight sysfs,
change Factory Backlight Current, or broaden LSPosed scope to CarSetting.

## Exact-device evidence

The corrected controller is based on the exact user-supplied privileged APKs:

| artefact | SHA-256 |
| --- | --- |
| `SystemUI.apk` | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| `CarSetting.apk` | `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71` |

The proprietary APKs and decoded output are not stored in this repository. The
repository-safe static contracts are `reference/exact-ts18-systemui-contract.json`
and `reference/exact-ts18-carsetting-contract.json`.

### Superseded assumption

Earlier source treated Topway command `516` as the ordinary physical Day/Night
brightness actuator. The exact CarSetting binary shows that this is not the
active physical-output path for the supplied build.

In the active CarSetting branch the brightness slider writes:

```text
Settings.System.SCREEN_BRIGHTNESS
```

with an effective raw range:

```text
CarSetting slider 0..225 -> screen_brightness 30..255
```

The module retains a safer, simpler managed logical range `1..10` and maps it
linearly:

```text
1  -> 30
2  -> 55
3  -> 80
4  -> 105
5  -> 130
6  -> 155
7  -> 180
8  -> 205
9  -> 230
10 -> 255
```

Managed logical level `0` remains prohibited.

### Topway mode/state contract

Command 258 remains the exact mode authority:

```text
0 = Auto
1 = Day
2 = Night
```

The exact CarSetting mode setter performs two stock operations:

```text
write(258, 1, selectedMode)
write(258, 128)
```

The controller reproduces both operations in that order on the SystemUI main
looper. The private meaning of the second operation is intentionally not named;
it is simply treated as a required second stage of the observed stock
transaction.

SystemUI state queries remain:

```text
write(258, 255)
write(516, 255)
```

Command 516 callbacks still expose useful Topway observation:

```text
daySlot        = packed & 0xff
nightSlot      = (packed >>> 8) & 0xff
effectiveNight = arg1 == 1
```

For this module those 516 values are **observation-only**. A 516 callback is not
physical brightness confirmation and the controller does not perform an
ordinary three-argument 516 brightness write.

## Runtime gates

Brightness mutation requires all of:

1. Android 10/API29 and the exact TS18 device token;
2. exact installed SystemUI SHA-256;
3. exact installed CarSetting SHA-256;
4. the required `TWSystemUI` reflection contract;
5. controller policy explicitly enabled.

The SystemUI and CarSetting hashes are verified off the SystemUI main thread.
Changing either privileged binary blocks managed brightness until the contract
is re-analysed. This prevents a firmware or CarSetting update from silently
reusing stale actuator assumptions.

## Policy behaviour

User modes remain:

- **Auto (stock)** — Topway mode 0 remains responsible for choosing effective
  Day/Night;
- **Day** — Topway mode 1;
- **Night** — Topway mode 2;
- **Set auto (scheduled)** — local-clock policy explicitly selecting Day/Night.

Day and Night managed levels can independently be `preserve current` or `1..10`.

### Fixed Day / Night

The controller first converges the Topway mode. It then applies the selected
managed Day or Night level through `Settings.System.SCREEN_BRIGHTNESS` and reads
the same setting back before reporting success.

### Set auto

The local-clock policy determines Day or Night, converges the corresponding
Topway mode transaction, then applies the corresponding managed physical level.
It does not depend on the vehicle's stock ILL/headlight Auto decision.

### Auto (stock)

Topway remains authoritative for the effective Day/Night decision. If both
managed levels are `preserve current`, no 516 observation is needed. If either
managed level is active, the module waits for a valid 516 observation before
choosing which physical level applies. If the effective state is unknown, it
fails open rather than guessing.

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
        ┌───────────┼────────────────────────────────────┐
        │ exact SystemUI + exact CarSetting hash gates   │
        │ physical read/write: SCREEN_BRIGHTNESS         │
        │ mode transport: Topway 258 two-stage sequence  │
        │ 516: effective Day/Night observation only      │
        │ bounded confirmation/retry + independent break │
        └───────────┬────────────────────────────────────┘
                    ▼
       Android Settings provider + existing TWSystemUI
```

The separately signed module application never receives platform settings
privileges. The physical write executes from the already injected, exact-gated
SystemUI process, whose exact manifest includes the relevant settings authority.
A failed `Settings.System.putInt` or mismatched readback is treated as a real
failure and never as success.

## Confirmation model

Mode and physical brightness use different confirmation authorities.

### Mode

1. send `258,1,<mode>`;
2. send `258,128`;
3. wait for the observed 258 state to match;
4. if unconfirmed, issue one bounded query before any retry;
5. allow at most one controlled transaction retry;
6. record `NO_258_CALLBACK` on non-convergence.

### Physical level

1. map logical 1..10 to raw 30..255;
2. write `Settings.System.SCREEN_BRIGHTNESS` once;
3. read `SCREEN_BRIGHTNESS` back;
4. if mismatched, perform one bounded re-read before retry;
5. allow at most one controlled physical write retry;
6. record `SCREEN_BRIGHTNESS_READBACK_MISMATCH` on non-convergence.

Repeated failures open only the brightness breaker. Compact touch and right-nav
remain independent.

## Diagnostics

Runtime status separates Topway semantic observation from the physical backend.
It includes:

- exact compatibility and hook count;
- CarSetting contract hash;
- transport readiness;
- current Topway mode;
- 516 callback timestamps, effectiveNight and observed packed slots;
- explicit `Topway516=observation-only` labelling;
- physical backend name;
- observed raw `screen_brightness`;
- last requested logical and raw level;
- last physical write/read timestamps;
- mode transaction stage-1/stage-2 timestamps and status;
- pending action and attempt count;
- callback/readback confirmation result;
- breaker state and failure count.

A dashboard acknowledgement still means **policy saved/consumed**, not hardware
success.

## Configuration source of truth

Persisted module policy remains:

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

## Safety and rollback

Do not respond to a failed physical test by:

- restoring ordinary Topway 516 physical writes;
- writing backlight sysfs;
- changing Factory Backlight Current;
- hooking CarSetting or `system_server`;
- broadening SystemUI/CarSetting hash matching;
- enabling logical level 0.

If the exact Settings write succeeds and readback converges but the panel still
does not visibly change, that is a new physical discrepancy. Capture the
runtime diagnostics and compare the stock CarSetting interaction before changing
actuator authority again.

## Physical validation still required

Before describing this correction as physically proven:

1. verify exact SystemUI and CarSetting compatibility reports READY;
2. confirm current raw `screen_brightness` is observed;
3. test a clearly different safe fixed Day level and require readback plus visible change;
4. test fixed Night likewise;
5. verify both 258 transaction stages and callback confirmation;
6. test stock Auto with distinct Day/Night managed levels and effective-night observation;
7. test local Set auto in both directions;
8. disable the controller and confirm stock CarSetting slider ownership is restored;
9. exercise headlights/ILL, reverse camera, fullscreen/projection and normal apps;
10. qualify SystemUI restart, warm reboot, cold boot and repeated ACC sleep/wake.
