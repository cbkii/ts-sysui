# Exact TS18 System UI

A systemless, reversible Android 10/API 29 implementation for CB's exact
Topway TS18 (`s9863a1h10`). It is not a generic TS10/TS18 modification.

The project keeps four authorities separate:

1. **Geometry** — a framework RRO reduces the OEM 58dp status-bar height to
   43dp. It does not change right-navigation dimensions.
2. **Visuals** — an independent SystemUI RRO reduces only three statically
   approved status icon/clock dimensions. It replaces the former recursive View
   scaling experiment.
3. **Collapsed input** — an LSPosed exact adapter runs after the stock Android-Q
   `StatusBarTouchableRegionManager` and narrows only an ordinary collapsed
   touch region.
4. **Right-nav media** — the same narrowly scoped LSPosed APK can add one
   reversible weighted media group to the exact Topway `navbar_left` host and
   command an existing Android `MediaController`.

The implementation never replaces `SystemUI.apk`, writes an Android partition,
hooks `system_server`, creates a playback service/session/queue/notification, or
takes audio focus.

## Exact SystemUI gate

Every exact behavioural mutation remains inert until the installed SystemUI APK
matches the controlling contract:

| Field | Required value |
|---|---|
| package | `com.android.systemui` |
| installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Android/API | Android 10 / 29 |
| device token | `s9863a1h10` |
| APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| shared UID | `android.uid.systemui` |
| media authority | `android.permission.MEDIA_CONTENT_CONTROL` |

`tools/ts18-systemui-contract.sh` performs the same installed-APK check before
an exact feature can be armed. The process check hashes in a background thread;
it never reads the protected APK on SystemUI's main thread.

## Hard collapsed-input boundary

An armed collapsed shade strip always:

- remains at least **64 physical pixels** from both top corners;
- is no wider than **20%** of full physical status/screen width;
- excludes the current right-navigation inset; and
- leaves stock behaviour when coordinates, stock region or special SystemUI
  state are ambiguous.

For the last-observed 1280x720 display and 55px right nav, the maximum half-open
strip is **[960,1216)**: 256px wide and 64px from the right physical corner.
Configuration may make the strip smaller, never larger or closer to a corner.

The exact adapter also keeps stock behaviour for keyguard/bouncer, expanded
shade, pinned or departing heads-up notifications, bubbles and force-collapsed
layout transitions. A separately labelled compatibility adapter remains for
diagnosis; it is not silently selected.

## Exact right-navigation contract

The decoded exact `navigation_bar.xml` contains vertical weighted
`com.android.systemui:id/navbar_left` children for screen/power, Home, Back,
Recents, optional app slot, volume up and volume down.

When explicitly enabled, runtime preflight requires that exact seven-child
topology, direct vertical `LinearLayout` ownership, uniform positive stock
weights, no unknown direct child, exact APK identity and a measured projected
cell of at least **56dp**. It then inserts one tagged module-owned group before
the volume controls. The group weight equals the action count; its Previous,
Play/Pause and Next cells have equal inner weights. No stock View, ID, listener
or `LayoutParams` is replaced or edited. Disablement, detach or reinflation
removes only the owned group.

Media selection is deterministic and sticky. Work is performed off the main
thread through:

`MediaSessionManager -> existing MediaController -> TransportControls`

One click schedules at most one supported command. There is no media-key or
Topway-private-command fallback for the same tap. Disabled commands remain
visible but disabled; Play/Pause reflects the selected controller state.

The exact weighted fit is a static/runtime expectation, not physical proof. At
153dpi and 720px height, six visible stock cells plus three media cells project
to about 80px each; seven stock cells project to about 72px.

## Deliverables and defaults

Release packaging produces independently recoverable artifacts:

- `TS18-StatusBar-Geometry-Magisk-*.zip`;
- `TS18-StatusBar-Visuals-Magisk-*.zip`;
- `TS18-StatusBar-Input-LSPosed-*.apk`; and
- a combined bundle with configuration, validation and recovery documentation.

| Control | Default / hard policy |
|---|---:|
| geometry RRO | separate install |
| visual RRO | separate install |
| compact policy generation | `4` |
| compact master/input | off / off |
| exact touch adapter | selected but inert |
| touch fraction | `0.20`, configurable `0.01..0.20` |
| corner gap | `>=64px` |
| nav policy generation | `2` |
| nav mutation/probe | off / off |
| nav actions | `previous,play_pause,next` |
| production nav target | `>=56dp` (`48dp` is STOP-only) |
| LSPosed scope | main `com.android.systemui` process only |

Use the root helper rather than editing `Settings.Global` by hand:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-on'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-observe'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-enable'
```

`input-on` and `nav-enable` verify the installed SystemUI contract first.
Runtime topology/state gates may still retain stock behaviour.

## Build and trust

The APK intentionally uses the legacy Xposed bridge supported by LSPosed:
`assets/xposed_init`, `IXposedHookLoadPackage`, `XposedBridge` and
`xposedminversion=82`. Local stubs are compile-only and CI verifies they are not
packaged. No signing private key or proprietary APK is committed.

Local prerequisites are JDK 17 and Android SDK platform/build-tools 35:

```bash
bash tools/bootstrap-gradle-wrapper.sh
bash tools/test-gradle-wrapper.sh
./gradlew --no-daemon clean test \
  :overlay:lintDebug :visual-overlay:lintDebug :lsposed:lintDebug \
  :overlay:assembleDebug :visual-overlay:assembleDebug :lsposed:assembleDebug
bash tools/test-apk-contract.sh lsposed/build/outputs/apk/debug/lsposed-debug.apk
ALLOW_DEBUG_SIGNING=1 bash tools/package-release.sh debug
```

See [`docs/INSTALL.md`](docs/INSTALL.md),
[`docs/VALIDATION.md`](docs/VALIDATION.md) and
[`docs/RECOVERY.md`](docs/RECOVERY.md). The complete implementation record is
[`docs/EXACT-TS18-SYSTEMUI-FINALISATION.md`](docs/EXACT-TS18-SYSTEMUI-FINALISATION.md).

## Validation status

Source/static/CI success proves neither touch delivery nor on-device RRO/idmap,
SystemUI restart, reboot, cold-boot, ACC sleep/wake, reverse-camera, projection,
call, keyguard or media-session behaviour. Those remain explicitly unverified
until recorded on the exact unit. Android 16 and generic FYT/UIS devices are not
targets.
