# Installation

## 0. Recovery prerequisite

Before enabling SystemUI hooks, confirm you can disable both the LSPosed APK and
the Magisk geometry module after a bad boot. Do not proceed without a recovery
route.

## 1. Geometry first

Install `TS18-StatusBar-Geometry-Magisk-*.zip` in Magisk and reboot with the
LSPosed component still disabled. Verify the RRO is active, the top bar/inset is
about 43 dp, the right navigation bar is unchanged, and normal apps receive the
smaller top inset. If geometry did not change cleanly, disable the Magisk module
and reboot; do not stack additional overlays blindly.

## 2. Install LSPosed in observation-only mode

Install `TS18-StatusBar-Input-LSPosed-*.apk`. Scope it to **only** the main
`com.android.systemui` process. Do not scope Android Framework/system_server,
DoFun, launcher, or other packages.

The APK is inert by default even after SystemUI restarts. Reboot/restart SystemUI
and confirm the `TS18StatusBar` installation line appears without a crash loop.
Optionally make the observation state explicit:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh observe'
```

Do not arm input until this baseline is stable.

## 3. Arm input only

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-on'
```

Restart SystemUI/reboot, then perform Stage B in `VALIDATION.md`. The default
strip is at most 20% of the physical width, at least 64 px from both top corners,
and excludes the current right navigation inset. Smaller widths down to 1% are
allowed.

If the live StatusBar window does not map 1:1 to the physical top edge, or stock
insets do not look like an ordinary collapsed bar, the hook deliberately leaves
stock input unchanged.

## 4. Arm optional visual scaling separately

Only after input is qualified:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-on'
```

Visual scaling remains experimental and defaults to `0.75`. Disable it
independently if any clock/icon/animation behaviour is wrong:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-off'
```

A SystemUI restart/reboot is the cleanest way to force immediate visual-state
reapplication/restoration after a setting change.

## Runtime configuration

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh touch-fraction 0.10'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh corner-gap 80'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-scale 0.75'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disarm'
```

`disarm` is the preferred persistent fail-open state: master/input/visual are all
off. The removed `ts18_statusbar_window_height_normalise` setting from v0.2 has
no runtime effect in v0.3+.
