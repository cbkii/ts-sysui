# Installation

## 0. Recovery prerequisite

Before enabling SystemUI hooks, confirm you can disable an LSPosed module and a
Magisk module after a bad boot. Do not proceed if you have no recovery route.

## 1. Build

For first-device development, GitHub Actions `Build` produces debug artefacts.
For durable use, configure one persistent signing key and use the `Release`
workflow; do not alternate signers for the same overlay package.

## 2. Geometry first

Install `TS18-StatusBar-Geometry-Magisk-*.zip` in Magisk and reboot.

Do **not** enable the LSPosed APK yet. Verify:

- the overlay is listed and enabled;
- collapsed StatusBar height is approximately 41–42 px at the current 153 dpi
  override, or otherwise corresponds to 43 dp at the current density;
- the right navigation bar is unchanged;
- apps receive the new smaller top inset correctly.

If geometry did not change, disable the Magisk module and reboot. Do not stack
additional overlays until the overlay/idmap policy is understood.

## 3. Input/visual second

Install `TS18-StatusBar-Input-LSPosed-*.apk`. In LSPosed:

- enable the module;
- scope it to **`com.android.systemui` only**;
- do not add Android Framework/system_server, launcher, DoFun or other packages.

Reboot. The default collapsed shade-initiation region is hard-bounded to no more
than 20% of the physical screen width and remains at least 64 px horizontally
clear of both top corners. On the 1280 px TS18 baseline with the 55 px right-nav
inset, the expected default region is x=960..1216. The visual leaf scale is 75%.

## 4. Optional runtime settings

Copy `tools/ts18-statusbar-config.sh` to the TS18 and run it under root.
Examples:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh status'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh corner-gap 80'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disable'
```

After a setting change, a SystemUI restart or reboot is the cleanest way to
ensure visual-state changes are fully reapplied.
