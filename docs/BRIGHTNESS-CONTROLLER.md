# TS18 brightness controller

## Scope

This feature targets CB's exact current Topway TS18 only. It does not claim
TS10/TS18-family protocol compatibility and it does not replace SystemUI, change
panel files, write sysfs, adjust LCD VCOM/AVDD, or alter Factory Backlight Current
calibration.

The controller is an optional capability of the existing SystemUI-scoped LSPosed
APK. It introduces no Android service, foreground service, notification, second
scheduler process or `system_server` hook.

## Current exact-device evidence — 2026-08-19

The brightness research capture established the present target as Android
10/API 29, `s9863a1h10`, Topway
`TS18.2.2_20241210.165912_WINDOW-THEME1`.

The retained current binaries used by the compatibility contract are:

| artefact | SHA-256 |
| --- | --- |
| `SystemUI.apk` | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| `CarSetting.apk` | `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71` |
| `framework.jar` | `9de541880ca8521db454133fbca8f7e1021f41b7e22756e844db880aea3b72bc` |
| `lights.sp9863a.so` (64-bit) | `ea3c27bef9ca5396f0e6fb6fda0aa1518caf55116e8e0883da5df4da6e93542c` |

Current SystemUI stock code uses
`com.android.systemui.tw.qspanel.QSPanelBrightness` and the existing
`com.android.systemui.tw.TWSystemUI` singleton. The same current package capture
shows `com.android.systemui` as a privileged `android.uid.systemui` package with
`android.permission.WRITE_SECURE_SETTINGS: granted=true`; the separately signed
LSPosed application itself does not assume or request that privilege.

### Recovered Topway contract

The current stock brightness slider writes:

```text
TWSystemUI.write(516, selector, level)
level: 0..10 in stock code
selector 0: day slot
selector 1: night slot
```

Command `516` callbacks carry both stored levels:

```text
dayLevel   = packed & 0xff
nightLevel = (packed >>> 8) & 0xff
effectiveNight = arg1 == 1
```

The current CarSetting binary changes brightness mode using:

```text
TWUtil/TW wrapper write(258, 1, mode)
mode 0 = Auto
mode 1 = Day
mode 2 = Night
```

Current SystemUI queries the semantic state with:

```text
write(258, 255)
write(516, 255)
```

The `258` callback exposes the mode in its third callback argument. These
contracts are pinned in unit tests; active mutation is additionally gated by the
exact current SystemUI SHA-256.

## Why Android brightness/sysfs are not the controller

During the current live interaction trace Android
`Settings.System.screen_brightness` remained `255` while Topway brightness moved
through its 0–10 values. The unit exposes both `sprd_backlight` and
`/sys/class/backlight/tw`, but the `tw` node did not always change when the
semantic Day/Night callback changed.

Therefore the controller treats the Topway `258`/`516` command/callback path as
the semantic authority. Android brightness and sysfs remain observation surfaces,
not actuators for this feature.

## Modes

The user-facing mode choices are:

- **Auto (stock)** — explicitly selects Topway mode `0`; the OEM ILL/headlight
  logic remains responsible for effective Day/Night selection.
- **Day** — explicitly selects Topway mode `1`.
- **Night** — explicitly selects Topway mode `2`.
- **Set auto (scheduled)** — deliberately avoids the unreliable stock Auto
  decision. It uses local clock time and explicitly changes Topway mode to Day or
  Night at the configured transition times.

`Set auto` supports any two distinct local transition times, including a Day
window that crosses midnight. It does not use sunrise/sunset/network location and
does not require an ambient-light sensor.

The first implementation performs an exact mode switchover at each configured
minute. It does **not** invent a PWM fade or alter backlight-current calibration.

## Brightness levels

Day and Night levels can each be managed independently. A level can also be left
as **preserve current**, in which case the controller does not rewrite that
stored Topway slot.

The controller deliberately permits managed levels **1..10 only**. Topway stock
supports `0`, but the present project has not physically qualified an automatic
recovery from a zero/no-backlight condition. Level `0` remains blocked until a
separate timed restore test proves recovery on the exact panel.

Factory Backlight Current minimum/maximum calibration is a separate hardware
range concern. It is not changed by this module.

## Runtime design

```text
TS18 Brightness Activity
        │  signed-permission config request
        ▼
exact-hash-gated SystemUI bridge ──► Settings.Global brightness policy
                                      │
                                      ▼
                              BrightnessPolicy
                                      │
                                      ▼
                          BrightnessController
                                      │
        ┌─────────────────────────────┼────────────────────────────┐
        │ waits for stock TWSystemUI.init / a valid callback      │
        │ observes StatusBarViewInit.sendTWCallBack(258/516)      │
        │ queries current 258/516 state                           │
        │ changes one semantic variable at a time                 │
        └─────────────────────────────┬────────────────────────────┘
                                      ▼
                       existing TWSystemUI / TWUtil
                                      ▼
                              Topway vendor/MCU
```

Before any active write, the injected controller verifies:

1. API 29;
2. exact `s9863a1h10` device/product family string;
3. exact current `SystemUI.apk` SHA-256;
4. required current `TWSystemUI` methods/classes;
5. the stock Topway transport has crossed its `init()` lifecycle boundary, or a
   valid Topway callback has directly proven that transport is live.

An unknown or changed SystemUI remains stock/fail-open. The private hooks are
also isolated from compact status-bar/right-nav runtime: a brightness hook
mismatch cannot roll back those independent hooks.

Hashing and configuration work run off the SystemUI main thread. Vendor
invocation is posted back to the SystemUI main looper. A callback/`init()` event
that arrives while compatibility hashing is still in progress is retained as
transport-readiness evidence and reconciled immediately after the exact-binary
check completes.

The controller waits for both mode and packed-level callbacks before acting. It
then changes at most one semantic variable per reconciliation step and re-queries
Topway state. Three repeated non-converging attempts open a brightness-only
process circuit breaker until SystemUI restarts.

Time/configuration changes and screen-on trigger reconciliation. Only this
feature's eight `Settings.Global` keys are observed; unrelated global settings do
not wake the brightness worker. `Set auto` also schedules the next local clock
transition directly; screen-on catches an ACC or sleep interval that crossed a
transition.

## Configuration source of truth and authority

The single persisted source is `Settings.Global`:

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

`-1` means preserve the current Topway slot. All mutation defaults off.

The APK includes a launcher activity named **TS18 Brightness**, but the ordinary
module application does **not** request `WRITE_SECURE_SETTINGS`. Instead it sends
one complete configuration request to a dynamic receiver injected into the
already-privileged current SystemUI process. The receiver:

- is registered only after exact SystemUI hash/class compatibility succeeds;
- requires the module-defined **signature-level**
  `au.com.cb.ts18.statusbar.input.permission.CONFIGURE_BRIGHTNESS` from the
  broadcaster, so unrelated apps cannot configure it;
- strictly validates mode, levels and transition minutes;
- writes `ts18_brightness_enabled=0` first, publishes a coherent policy, then
  publishes the policy generation and final enable bit last;
- returns its acknowledgement through the request's framework `ResultReceiver`
  Binder callback rather than an exported/global result broadcast. A timeout is
  treated as failure; the UI never assumes the request succeeded.

This keeps privileged Settings ownership in the exact process that already has
it and avoids granting a protected Android permission to a normal APK. No
manifest receiver/service is exported by SystemUI and no second configuration
provider is introduced.

A bounded root shell helper remains the recovery/fallback interface and writes
the same Settings.Global source of truth directly as root.

## Safety and rollback

Persistent kill switch:

```sh
settings put global ts18_brightness_enabled 0
```

Fallback helper:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh disable'
```

If private class/hash verification fails, no active Topway write occurs and the
normal launcher Activity receives no success acknowledgement. If the runtime
breaker opens, it also stops mutation for that SystemUI process. Disable the
LSPosed scope/module and reboot if SystemUI itself is unstable.

Do not work around a compatibility refusal by removing the exact hash gate. A
new firmware requires a fresh read-only capture and contract comparison first.

## Physical validation still required

Source/static/CI success is not TS18 runtime proof. Before release qualification,
perform the brightness stages in `VALIDATION.md`, including:

- observation-only hook load and configuration-bridge acknowledgement;
- fixed Day and fixed Night with safe visible levels;
- a near-future Set-auto Day/Night transition in both directions;
- stock slider coexistence;
- headlights/ILL on/off;
- reverse-camera state;
- SystemUI restart and launcher restart;
- warm reboot, cold boot and ACC sleep/wake;
- persistent kill switch and LSPosed disable recovery.

The Factory Backlight Current min/max remains a separate unresolved input if a
future feature is intended to make level 10 physically brighter than the current
panel calibration.
