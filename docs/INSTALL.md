# Installation and staged arming

This procedure targets only the exact Topway `s9863a1h10` Android 10/API 29
unit. Do not install on a generic TS10/TS18 or Android 16 system.

## 1. Prepare recovery before changing SystemUI

- Confirm a working Magisk module disable/remove path from recovery or adb.
- Confirm LSPosed safe mode/module-disable access.
- Retain the stock boot image and a current list of enabled modules.
- Copy the bundle scripts to `/storage/emulated/0/Download/`.
- Do not remove an OEM RRO, replace `SystemUI.apk` or write a partition.

Record the untouched baseline:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-systemui-contract.sh'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-validate.sh'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
```

STOP if the contract script does not report `SUPPORTED` with the exact SHA-256.

## 2. Install geometry only

Install `TS18-StatusBar-Geometry-Magisk-*.zip`, reboot and verify that its
framework RRO is present and the status region is approximately 43dp (about
41px at the last-observed 153dpi). The right nav must remain unchanged.

Run the validator, then exercise shade expansion, notifications, keyguard,
heads-up, rotation if supported, reverse camera, projection and calls. Disable
the geometry module immediately if SystemUI/boot stability regresses.

## 3. Qualify the independent visual RRO

Only after geometry is stable, install
`TS18-StatusBar-Visuals-Magisk-*.zip` and reboot. Inspect:

```sh
su -c 'cmd overlay list --user 0'
su -c 'dumpsys package com.android.systemui | grep -E "resourceDirs|overlay paths"'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-status'
```

It may change only status icon/clock size. Any idmap rejection, missing overlay,
nav/layout change or unreadable content is a STOP: disable this visual module
and retain stock visuals. Do not bypass overlay policy by replacing/resigning
SystemUI.

## 4. Install LSPosed APK inertly

Install `TS18-StatusBar-Input-LSPosed-*.apk`. In LSPosed, select only
`com.android.systemui`; do not select Android Framework or `system_server`.
Reboot with all settings still off and confirm logs show hook installation plus
the asynchronous exact contract result without a crash loop.

Enter compact observation mode:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh observe'
```

Reboot or restart SystemUI using the unit's already-proven method. Do not use an
unverified force-stop sequence on the protected package.

## 5. Arm exact collapsed input

The default maximum is 20%; configure a smaller value first if desired:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh touch-adapter exact'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh touch-fraction 0.10'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh corner-gap 64'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-on'
```

`input-on` re-verifies the installed APK. The process adapter may still keep the
stock region for special/ambiguous states. Validate that apps receive the top
edge outside the strip while the shade works inside it. Restore immediately
with `input-off` if either side fails.

The `compatibility` touch adapter is a labelled diagnostic choice only. Do not
use it to bypass an exact reflection/identity STOP.

## 6. Observe the exact right nav

Keep nav mutation off first:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-observe'
su -c 'sh /storage/emulated/0/Download/ts18-right-nav-evidence.sh'
```

Confirm the exact `navbar_left` host, all OEM functions, weighted direct-child
topology and reinflation behaviour. Then disable the probe:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
```

## 7. Arm media controls incrementally

Start with Play/Pause only and a production target of 56dp:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-actions play_pause'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-min-touch-dp 56'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-enable'
```

After restart, confirm every OEM control remains present/correct, the added cell
is at least 56dp, no-session state is disabled, one tap causes one action and
disable/reinflation restores stock exactly. Only then add actions:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-actions previous,play_pause,next'
```

Restart/revalidate after action-order changes. `nav-enable` refuses `none`, an
invalid list or a configured target below 56dp even though 48dp remains the hard
STOP floor for diagnostics.

## 8. Acceptance sequence

Complete the staged matrix in `VALIDATION.md`: SystemUI restart, reboot, cold
boot, ACC sleep/wake and operational states. A source/CI pass or one successful
tap is not release qualification.
