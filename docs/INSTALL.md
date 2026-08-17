# Installation

## 0. Recovery prerequisite

Before enabling SystemUI hooks, confirm you can disable both the LSPosed APK and
the Magisk geometry module after a bad boot. Do not proceed without a recovery
route.

Right-nav observation is independently gated and must remain optional. A
right-nav observation failure must not be worked around by widening LSPosed scope
or adding Android Framework/system_server.

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

The APK is inert by default even after SystemUI restarts. Compact status-bar
v0.3+ also requires `ts18_statusbar_policy_version=3`, so stale v0.2
Settings.Global values are ignored. The configuration helper migrates safely by
clearing compact master/input/visual flags before writing policy generation 3.

Reboot/restart SystemUI and confirm the `TS18StatusBar` installation line appears
without a crash loop. Optionally make the compact observation state explicit:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh observe'
```

Do not arm compact input until this baseline is stable.

## 3. Arm compact input only

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-on'
```

Restart SystemUI/reboot, then perform Stage B1 in `VALIDATION.md`. The default
strip is at most 20% of the physical width, at least 64 px from both top corners,
and excludes the current right navigation inset. Smaller widths down to 1% are
allowed.

If the live StatusBar window does not map 1:1 to the physical top edge, or stock
insets do not look like an ordinary collapsed bar, the hook deliberately leaves
stock input unchanged.

## 4. Arm optional visual scaling separately

Only after compact input is qualified:

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

## 5. Optional right-nav observation

v0.4 can collect a bounded read-only hierarchy for the live
`TYPE_NAVIGATION_BAR`. This stage **does not add media buttons or alter the
navigation bar**.

Before using it, re-check current Magisk/Zygisk/LSPosed state and other SystemUI
writers. Then:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-status'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-observe'
```

Restart SystemUI/reboot and reproduce a Stage N0 state. The bundled bounded
collector is the preferred way to retain current SystemUI identity, package/window
state, right-nav probe output and the exact current SystemUI APK hash/copy:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-right-nav-evidence.sh'
```

It writes under `/storage/emulated/0/Download/TS18-StatusBar/`. Repeat the capture
after materially different UI/lifecycle states listed in Stage N0 rather than
assuming one snapshot represents every state.

Disable probing when the capture is complete:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
```

`nav-enable` intentionally stops with a safety error in this milestone. A
clickable right-nav feature remains blocked by the exact SystemUI APK and physical
evidence gates in `RIGHT-NAV-MEDIA-ROADMAP.md`.

## Runtime configuration

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh touch-fraction 0.10'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh corner-gap 80'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-scale 0.75'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-actions next,play_pause,previous'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-min-touch-dp 56'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disarm'
```

`disarm` is the preferred persistent compact fail-open state: compact
master/input/visual are all off. Right-nav probe state is independent; use
`nav-reset` or `nav-probe-off` to return it to defaults.

## Local build wrapper bootstrap

The repository does not trust or commit a generated `gradle-wrapper.jar`. Before
the first local `./gradlew` invocation, provision the official Gradle 8.9 wrapper
JAR and verify it against Gradle's published SHA-256:

```sh
bash tools/bootstrap-gradle-wrapper.sh
bash tools/test-gradle-wrapper.sh
./gradlew --version
```

The bootstrap refuses to overwrite a wrapper JAR whose checksum is unknown.
Delete a suspect local JAR manually before retrying rather than silently replacing
an executable binary.
