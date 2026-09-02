# Installation and staged arming

This procedure targets only the exact Topway `s9863a1h10` Android 10/API 29
unit. Do not install on a generic TS10/TS18 or Android 16 system.

## 1. Prepare recovery

Before changing SystemUI-adjacent behaviour:

- confirm Magisk module disable/remove recovery;
- confirm LSPosed safe mode/module-disable access;
- retain the stock boot image and current enabled-module list;
- do not remove an OEM RRO, replace `SystemUI.apk` or write an Android partition.

The exact SystemUI contract remains:

```text
/system/priv-app/SystemUI/SystemUI.apk
SHA-256 668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f
```

Managed brightness additionally depends on the exact supplied CarSetting build:

```text
package com.dofun.carsetting
SHA-256 06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

A mismatch is a STOP for the affected private behaviour.

## 2. Migrate legacy split Magisk modules once

Normal releases use one Magisk module: `ts18_sysui`.

If either old module is still installed:

```text
ts18_statusbar_geometry
ts18_statusbar_visuals
```

copy the bundled helper to Downloads and run:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-migrate-magisk-modules.sh'
```

It marks only those two exact legacy IDs for normal Magisk removal. It does not
delete module directories directly. Reboot and confirm the old IDs are gone
before installing the combined module.

If another module may own either overlay path, STOP and inspect the current
Magisk module list instead of deleting files.

## 3. Install the combined SystemUI RRO module

Install:

```text
TS18-SystemUI-Magisk-v<version>-release.zip
```

The module contains both independently built RRO APKs:

```text
/product/overlay/TS18StatusBarGeometry.apk
/product/overlay/TS18StatusBarVisuals.apk
```

The installer validates API29, `s9863a1h10`, both payloads and legacy-module
conflicts. Reboot.

After reboot verify physically that:

- the top status region is reduced toward the intended ~43dp geometry;
- only status icons/clock are visually reduced;
- the right navigation strip has not been widened or lost any OEM control; and
- SystemUI/launcher/reverse-camera boot behaviour remains stable.

The dashboard can report whether the systemless overlay payload paths are
mounted, but payload presence is not proof of idmap/resource application.

## 4. Install and scope the LSPosed APK

Install/update:

```text
TS18-SystemUI-LSPosed-v<version>-release.apk
```

In LSPosed scope **only the main `com.android.systemui` process**. Do not select
Android Framework, `system_server`, CarSetting, TWCore, Launcher,
`com.tw.service*` or other vendor apps.

Reboot so SystemUI starts with the module from the beginning of its lifecycle.

## 5. Open TS18 System UI and establish readiness

Launch **TS18 System UI**. Before enabling behaviour confirm:

- SystemUI bridge = ready;
- exact SystemUI identity = `SUPPORTED`;
- nav hook is installed and the navbar root/host are observable;
- managed brightness compatibility reports the exact CarSetting contract ready;
- no nav/brightness breaker is open.

If the bridge does not answer, treat the module as not loaded/compatible. Do not
widen LSPosed scope as a workaround.

## 6. Compact collapsed touch

The compact path is already physically proven on the exact unit, but re-check it
after installation:

1. begin with a small shade trigger width such as 10%;
2. keep the hard corner gap at 64px;
3. enable compact collapsed-shade touch routing;
4. apply.

Validate shade initiation only inside the configured top-right region and app
top-edge interaction outside it. Keyguard, expanded shade, heads-up/bubbles and
transition states must retain stock handling.

The hard contract remains <=20% physical width and >=64px from either top
corner. The compatibility adapter is an engineering fallback only, never a way
to bypass an exact-contract STOP.

## 7. Qualify the right sidebar

Before enabling controls, use **Refresh live status** and inspect:

- exact nav hook/root/host state;
- live direct-child resource names;
- preflight STOP reason;
- host width/height/density;
- projected vertical cell size;
- stock child summary; and
- media controller selection state.

The current exact contract requires these direct children:

```text
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce
```

Known optional direct children are `navbar_guanping` and `navbar_app`. Unknown or
duplicate direct children remain a STOP.

### First physical activation

Select only **Play / Pause**, enable custom media controls and Apply.

Acceptance before adding more actions:

- a Play/Pause cell appears in the existing right strip;
- it remains visible-but-disabled with no usable media session while the stock
  panel itself is visible;
- all OEM controls remain present and correct;
- projected vertical cell >=56dp;
- horizontal control uses the existing full strip width and is never below the
  48dp hard floor;
- starting a media session enables it when supported;
- one tap causes exactly one play or pause operation; and
- disabling the feature removes only the module-owned cell.

Then enable Previous and Next and repeat the checks. Configuration changes should
reconcile immediately; a SystemUI restart is not required merely for settings to
be consumed.

## 8. Qualify brightness

For this exact CarSetting build, the active physical slider authority is:

```text
Settings.System.SCREEN_BRIGHTNESS
raw range 30..255
```

The dashboard retains logical managed values 1..10, mapped to:

```text
1=30, 2=55, 3=80, 4=105, 5=130,
6=155, 7=180, 8=205, 9=230, 10=255
```

Topway 258 remains mode authority. Topway 516 is observation-only for packed
Day/Night slots and effective Day/Night; it does not prove physical convergence.

Before enabling the controller confirm the dashboard shows:

- exact SystemUI and CarSetting compatibility;
- Topway transport readiness;
- current/known 258 mode;
- 516 observation state/effectiveNight where available;
- physical backend = `Settings.System.SCREEN_BRIGHTNESS`;
- current raw screen brightness;
- requested logical/raw values; and
- brightness breaker closed.

### Fixed Day

1. enable **Set day brightness level** and choose a safe value 1..10;
2. select Day and enable the controller;
3. Apply;
4. require the observed stock transaction to be issued in order:
   `write(258,1,1)` then `write(258,128)`;
5. require 258 mode confirmation;
6. require raw `SCREEN_BRIGHTNESS` readback to equal the requested mapped value;
7. confirm the physical screen brightness visibly matches the intended result.

### Fixed Night

Repeat with Night and a deliberately different safe logical value. Require 258
mode confirmation, raw readback convergence and a physically distinct visible
result.

`NO_258_CALLBACK`, `SCREEN_BRIGHTNESS_READBACK_MISMATCH`, transport-not-ready,
exact CarSetting mismatch or breaker-open is a real STOP reason. Policy
persistence alone is never success.

The **Test Day** / **Test Night** buttons provide controlled tests and remember
the previous policy for the in-app Restore action. Managed level 0 remains
unsupported.

### Stock Auto

With managed Day/Night values configured, stock Topway Auto remains responsible
for deciding effective Day/Night. The module may apply the corresponding
physical level only after a valid 516 effective-state observation. Unknown state
must fail open rather than guess.

### Set auto (scheduled)

Only after fixed Day and Night pass should local-clock Set-auto be tested with
near-future transitions in both directions. Each boundary should produce only the
required mode/physical convergence work, not continuous rewriting.

## 9. Lifecycle acceptance

After each feature works independently, validate the intended combined setup:

1. normal launcher/app use;
2. SystemUI restart using an already-proven method;
3. warm Android reboot;
4. cold boot/full power removal where safe;
5. repeated ACC sleep/wake;
6. reverse camera;
7. calls/audio-route changes;
8. projection/immersive mode; and
9. representative long-duration use.

Source/CI success or a single successful tap is not physical release
qualification. Record results against `VALIDATION.md`.

## Recovery helpers

For engineering/recovery, the bundle includes:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-systemui-contract.sh'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh status'
```

Do not use repeated unbounded `pm`, `am`, `cmd overlay` or `dumpsys` calls on
this TS18 as a substitute for the in-process dashboard status path.