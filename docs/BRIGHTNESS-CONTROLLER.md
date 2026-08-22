# TS18 brightness controller

## Scope

This feature targets CB's exact current Topway TS18 only. It does not claim
TS10/TS18-family compatibility and it does not replace SystemUI, change panel
files, write backlight sysfs, adjust LCD VCOM/AVDD or alter Factory Backlight
Current calibration.

The controller remains an optional capability of the SystemUI-scoped LSPosed
APK. It introduces no Android service, foreground service, notification, second
scheduler process or `system_server` hook.

## Exact-device evidence

The retained current target is Android 10/API29, `s9863a1h10`, Topway
`TS18.2.2_20241210.165912_WINDOW-THEME1`.

| artefact | SHA-256 |
| --- | --- |
| `SystemUI.apk` | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| `CarSetting.apk` | `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71` |
| `framework.jar` | `9de541880ca8521db454133fbca8f7e1021f41b7e22756e844db880aea3b72bc` |
| `lights.sp9863a.so` (64-bit) | `ea3c27bef9ca5396f0e6fb6fda0aa1518caf55116e8e0883da5df4da6e93542c` |

Stock SystemUI uses `com.android.systemui.tw.qspanel.QSPanelBrightness` and the
existing `com.android.systemui.tw.TWSystemUI` singleton. Current package evidence
also establishes the SystemUI process as privileged/shared-SystemUI and able to
write the persisted policy; the separately signed LSPosed application does not
request `WRITE_SECURE_SETTINGS`.

### Recovered Topway contract

Stock brightness writes:

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

CarSetting changes mode using:

```text
write(258, 1, mode)
mode 0 = Auto
mode 1 = Day
mode 2 = Night
```

SystemUI queries semantic state with:

```text
write(258, 255)
write(516, 255)
```

The 258 callback exposes the current mode in its third callback argument.

## Why Android brightness/sysfs remain non-authoritative

During the retained live interaction trace, Android
`Settings.System.screen_brightness` stayed at `255` while Topway brightness
changed through its 0–10 semantic values. The `tw` backlight node also did not
always track semantic Day/Night changes.

Therefore the ordinary controller continues to treat 258/516 as the semantic
authority. Android brightness, backlight sysfs, Factory Backlight Current and
screen-power command 33281 remain separate observation/control domains, not
automatic fallbacks.

## Physical 0.5.1 finding

The first physical 0.5.1 installation showed no observable brightness difference
when applying Day versus Night. That finding does not by itself disprove the
258/516 contract because the 0.5.1 UI could save a mode while both Day/Night
levels remained `preserve current`, and its acknowledgement proved only policy
persistence rather than transport/callback/hardware convergence.

The remediation therefore makes every stage observable and callback-confirmed.

## Modes and levels

User modes remain:

- **Auto (stock)** — Topway mode 0;
- **Day** — Topway mode 1;
- **Night** — Topway mode 2;
- **Set auto (scheduled)** — local-clock policy explicitly selecting Day/Night.

Day and Night levels can remain **preserve current** or be managed from **1..10**.
Managed 0 remains blocked until a separate timed no-backlight recovery test is
physically proven.

The dashboard now displays the detected 516 Day/Night values. If they are equal,
it explicitly warns that changing mode alone will not visibly change brightness.

## Runtime architecture

```text
TS18 System UI dashboard
        │ signature-protected package-targeted request
        ▼
SystemUiBridge inside exact SystemUI
        │
        ├── coherent Settings.Global policy transaction
        ├── private ResultReceiver acknowledgement
        └── live status snapshot
                    │
                    ▼
             BrightnessController
                    │
        ┌───────────┼─────────────────────────────┐
        │ exact SystemUI/class gate               │
        │ waits for TWSystemUI.init/callback      │
        │ observes sendTWCallBack(258/516)        │
        │ observes stock/module Topway writes     │
        │ callback-first pending-action confirm   │
        └───────────┬─────────────────────────────┘
                    ▼
             existing TWSystemUI
                    ▼
              Topway vendor/MCU
```

The bridge can register while exact identity is still `CHECKING` so the dashboard
can explain why mutation is blocked. A mutating request is still rejected unless
`ExactSystemUiIdentity` is `SUPPORTED`.

## User-visible runtime state

The dashboard distinguishes:

```text
HOOK/IDENTITY
 -> TRANSPORT_READY
 -> MODE_STATE_KNOWN
 -> LEVEL_STATE_KNOWN
 -> CONFIG_ENABLED
 -> ACTION_PENDING
 -> CALLBACK_CONFIRMED
 -> ACTIVE/SETTLED
```

and explicit `BLOCKED:` / `ERROR:` states.

The runtime status includes:

- hook count and exact compatibility;
- transport readiness/time;
- mode-known and levels-known;
- current Topway mode and `effectiveNight`;
- detected Day/Night values;
- last 258 callback timestamp;
- last 516 callback timestamp;
- last observed stock 258/516 write;
- last module action/write time;
- pending action/attempt count;
- confirmation result;
- brightness breaker state/failure count.

A configuration acknowledgement means **policy saved/consumed**, not hardware
success. The UI refreshes runtime status separately and reports semantic
confirmation.

## Callback-first action confirmation

The old short repeated-write loop is replaced by one pending action.

For each required semantic change:

1. send exactly one Topway write;
2. wait up to 1.5s for the corresponding callback/state predicate;
3. if unconfirmed, issue one bounded semantic 258/516 query;
4. wait a further 1.5s;
5. if still unconfirmed, issue at most one controlled write retry;
6. allow a longer 2.5s final confirmation window;
7. on failure, report a semantic reason and only then record one breaker failure.

Failure reasons distinguish at least:

```text
NO_258_CALLBACK
NO_516_CALLBACK
```

This avoids declaring an OEM MCU path broken simply because it did not confirm
within 450ms. The overall brightness breaker remains process-local and isolated
from compact/nav behaviour.

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

The normal configuration path is the **TS18 System UI** dashboard. The bridge:

- is a dynamic receiver inside SystemUI, not a manifest SystemUI component;
- is package-targeted;
- requires the module's signature-level configuration permission;
- requires a private framework `ResultReceiver` in every request;
- validates the complete requested policy;
- writes enable off first, publishes coherent fields/generation, then enable
  last;
- returns live runtime status with the acknowledgement;
- never grants the normal APK Android privileged settings authority.

The bounded root helper remains recovery/development fallback:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh disable'
```

## Test Day / Test Night

The dashboard provides explicit test actions. A test requires the corresponding
managed Day or Night level to be selected from the safe 1..10 range.

Before the test, the dashboard retains the previous policy in memory and enables
a Restore action. The test then selects the requested fixed mode and waits for
live callback confirmation. It does not claim success merely because the policy
was written.

These controls are a physical qualification aid, not a substitute for the
lifecycle matrix.

## Safety and rollback

If exact identity/classes fail, no active Topway write occurs. If transport is
not ready or required callbacks are absent, the dashboard reports the block. If
the bounded confirmation sequence fails repeatedly, the brightness-only breaker
opens until SystemUI restarts.

Do not remove the hash gate, widen LSPosed scope or substitute a generic Android
brightness/sysfs write to make the test pass.

## Physical validation still required

Before release qualification:

1. verify exact identity/hook/transport state in the dashboard;
2. inspect detected Day/Night values;
3. choose deliberately distinct safe non-zero values;
4. test fixed Day and require 258/516 confirmation plus a physical change;
5. test fixed Night the same way;
6. test Set auto in both directions;
7. test stock slider/CarSetting coexistence while module disabled;
8. exercise headlights/ILL, reverse camera and normal apps;
9. qualify SystemUI restart, warm reboot, cold boot and ACC sleep/wake;
10. verify the brightness kill switch/LSPosed recovery path.

If a module-generated 258/516 change is semantically callback-confirmed but
physical brightness still does not change, STOP and collect a narrow stock vs
module marker trace before reconsidering the actuator.
