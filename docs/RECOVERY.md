# Recovery

Compact input, right-nav media and brightness behaviour remain independently
disableable in the LSPosed layer. Geometry and visual RROs are now packaged in
one user-facing Magisk module, `ts18_sysui`; recover the narrowest behavioural
layer first before disabling the combined RRO module.

## Behavioural recovery while Android is usable

Use **TS18 System UI** first when the dashboard is responsive:

- disable custom right-sidebar controls;
- disable compact collapsed-shade routing;
- disable the brightness controller;
- Refresh live status and confirm the corresponding feature is OFF/stock.

The root helpers remain an independent fallback:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-disable'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disable'
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh disable'
```

`nav-disable` removes only the module-owned media group on reconciliation; stock
controls are never reconstructed. `input-off` leaves the stock touch manager's
region intact. Brightness disable stops policy rewriting and returns ownership to
stock Topway behaviour.

If immediate convergence cannot be confirmed, reboot rather than using an
unverified force-stop sequence against protected SystemUI.

## Combined RRO recovery

For a geometry or visual RRO problem, disable Magisk module:

```text
ts18_sysui
```

and reboot. This disables both project RRO payloads together; it does not touch
the OEM overlay or `SystemUI.apk`.

Do not delete an OEM overlay, write `/product`, replace/resign SystemUI or alter
platform signing to repair an RRO.

## Legacy split-module migration/recovery

Old releases used:

```text
ts18_statusbar_geometry
ts18_statusbar_visuals
```

The combined installer deliberately refuses to coexist with active legacy IDs.
The bundled migration helper marks only those two exact IDs for normal Magisk
removal:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-migrate-magisk-modules.sh'
```

Reboot before installing `ts18_sysui`. The helper does not recursively delete
`/data/adb/modules` or other module state.

## LSPosed/SystemUI crash-loop recovery

Use the already-tested LSPosed safe mode or recovery route to disable package:

```text
au.com.cb.ts18.statusbar.input
```

then reboot. Keep LSPosed scope limited to the main `com.android.systemui`
process; do not add Framework/system_server/vendor processes as a recovery
experiment.

If instability remains, disable `ts18_sysui` and reboot. This returns both
project overlays to stock without changing partitions.

## Brightness-specific recovery

The production range remains 1..10. Level 0 is not supported.

If brightness reports `ACTION_PENDING`, `NO_258_CALLBACK`, `NO_516_CALLBACK`, a
Topway-query/write error or breaker-open:

1. disable brightness only;
2. verify stock SystemUI/CarSetting brightness still works;
3. save the dashboard diagnostic report;
4. reboot SystemUI/Android using the already-proven route before another test.

Do not switch automatically to Android `screen_brightness`, backlight sysfs,
Factory Backlight Current or screen-power command 33281. They are separate
control domains on this unit.

## Right-nav-specific recovery

If the dashboard reports a preflight STOP, leave nav mutation off and retain the
reported host/stock summary. Do not hide, recreate or resize OEM controls to make
room.

A valid production injection requires:

- recognised mandatory stock controls;
- no unknown direct child;
- safe weighted vertical geometry >=56dp;
- existing horizontal strip width >=48dp;
- exactly one module-owned group.

Any OEM-control regression is an immediate nav-disable STOP.

## STOP and remain stock when

- installed SystemUI SHA/device/API differs;
- an RRO is rejected or renders the wrong resource domain;
- exact touch reflection/coordinate/state preflight fails;
- navbar topology is ambiguous or an OEM function changes;
- one tap yields multiple media actions;
- Topway brightness writes cannot receive semantic confirmation;
- a confirmed 258/516 state change still produces no physical brightness change;
- SystemUI instability, input lockout or boot/ACC regression appears.

For the last brightness case, collect a narrow stock-vs-module trace before
changing actuator design. Recovery success means stable stock behaviour through
the required restart/reboot boundary, not merely absence of a visible module
control.
