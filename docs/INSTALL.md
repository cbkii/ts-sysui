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

A mismatch is a STOP for behavioural mutation.

## 2. Migrate legacy split Magisk modules once

Normal releases now use one Magisk module: `ts18_sysui`.

If either old module is still installed:

```text
ts18_statusbar_geometry
ts18_statusbar_visuals
```

copy the bundled helper to Downloads and run:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-migrate-magisk-modules.sh'
```

It marks only those two exact legacy IDs for Magisk removal. It does not delete
module directories directly. Reboot and confirm the old IDs are gone before
installing the combined module.

If you are unsure whether another module owns either overlay path, STOP and
inspect the current Magisk module list instead of deleting files.

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

After reboot verify visually that:

- the top status region is reduced toward the intended ~43dp geometry;
- only status icons/clock are visually reduced;
- the right navigation strip has not been widened or lost any OEM control;
- SystemUI/launcher/reverse-camera boot behaviour remains stable.

The dashboard reports whether the systemless overlay payload paths are mounted.
That is not by itself proof of successful idmap/resource application, so the
rendered result remains the primary physical check.

## 4. Install and scope the LSPosed APK

Install/update:

```text
TS18-SystemUI-LSPosed-v<version>-release.apk
```

In LSPosed scope **only the main `com.android.systemui` process**. Do not select
Android Framework, `system_server`, `com.tw.service*` or other vendor apps.

Reboot so the SystemUI process starts with the module from the beginning of its
lifecycle.

## 5. Open TS18 System UI and establish readiness

Launch **TS18 System UI**. The dashboard is now the normal configuration path.

Before enabling behaviour confirm:

- SystemUI bridge = ready;
- exact identity = `SUPPORTED`;
- nav hook is installed and the navbar root is seen;
- brightness hooks are installed;
- no nav/brightness breaker is open.

If the bridge does not answer, treat the module as not loaded/compatible. Do not
widen LSPosed scope as a workaround.

The root scripts remain recovery/development helpers only.

## 6. Compact collapsed touch

In **Compact status bar**:

1. begin with a small shade trigger width such as 10%;
2. keep the hard corner gap at 64px;
3. enable compact collapsed-shade touch routing;
4. apply.

Validate that the shade initiates only inside the configured top-right region
and that apps receive top-edge touches outside it. Keyguard, expanded shade,
heads-up/bubbles and transition states must retain stock handling.

The hard contract remains <=20% physical width and >=64px from either top
corner. A compatibility adapter remains an engineering fallback only; never use
it to bypass an exact-contract STOP.

## 7. Qualify the right sidebar

Before enabling controls, use **Refresh live status** and inspect the sidebar
status. The dashboard exposes:

- exact nav hook/root/host state;
- preflight STOP reason;
- host width/height/density;
- projected vertical cell size;
- stock child summary;
- media controller selection state.

The current exact-device implementation requires Home, Back, Recents, Volume+
and Volume− as direct recognised stock controls. Known screen/power and app-slot
children may be conditional/absent. Unknown direct children still cause a STOP.

### First physical activation

Select only **Play / Pause**, enable custom media controls and Apply.

Acceptance before adding more actions:

- a Play/Pause cell appears in the existing right strip;
- it remains visible-but-disabled with no usable media session;
- all OEM controls remain present and correct;
- projected vertical cell >=56dp;
- horizontal control uses the existing full strip width and is never below the
  48dp hard floor;
- starting a media session enables it when supported;
- one tap causes exactly one play or pause operation;
- disabling the feature removes only the module-owned cell.

Then enable Previous and Next and repeat the checks. Configuration changes should
reconcile immediately; a SystemUI restart is no longer required merely for the
module to notice new nav settings.

## 8. Qualify Topway brightness

Open **Topway brightness** and first read the detected state.

The dashboard must show:

- brightness runtime state;
- transport readiness;
- whether mode and levels are known;
- detected Day and Night values;
- current Topway mode/effective-night state;
- latest hardware confirmation result.

If Day and Night detected values are equal, switching mode alone is expected to
look identical. Configure deliberately different, safe non-zero managed values
for physical testing.

### Fixed Day

1. enable **Set day brightness level** and choose a clearly visible value 1..10;
2. leave Night unchanged or use a different safe value;
3. select Day and enable the controller;
4. Apply;
5. wait for the dashboard to progress to `CALLBACK_CONFIRMED` / `ACTIVE/SETTLED`.

### Fixed Night

Repeat using Night and require semantic confirmation. A message such as
`NO_258_CALLBACK`, `NO_516_CALLBACK`, transport-not-ready or breaker-open is a
real STOP reason; do not interpret policy persistence as working brightness.

The **Test Day** / **Test Night** buttons provide a controlled one-variable test
and remember the previous policy for the in-app Restore action. Managed level 0
remains unsupported.

Only after fixed Day and Night are physically distinct and callback-confirmed
should Set auto be tested with near-future transition times.

## 9. Lifecycle acceptance

After each feature works independently, validate the intended combined setup in
this order:

1. normal launcher/app use;
2. SystemUI restart using an already-proven method;
3. warm Android reboot;
4. cold boot/full power removal where safe;
5. repeated ACC sleep/wake;
6. reverse camera;
7. calls/audio-route changes;
8. projection/immersive mode;
9. representative long-duration use.

Source/CI success or a single successful tap is not physical release
qualification. Record results against `VALIDATION.md`.

## Recovery helpers

For engineering/recovery, the bundle still includes:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-systemui-contract.sh'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh status'
```

Do not use repeated unbounded `pm`, `am`, `cmd overlay` or `dumpsys` calls on
this TS18 as a substitute for the in-process dashboard status path.
