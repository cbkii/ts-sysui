# Recovery

Compact input, right-nav media, XTService observation and brightness behaviour
remain independently failure-isolated in the LSPosed layer. Geometry and visual
RROs are packaged in the single user-facing Magisk module `ts18_sysui`; recover
the narrowest affected behavioural layer first.

## Behavioural recovery while Android is usable

Use **TS18 System UI** first when the dashboard is responsive:

- disable custom right-sidebar controls;
- disable compact collapsed-shade routing;
- disable the brightness controller;
- refresh live status and confirm the corresponding feature is OFF/stock.

The root helpers remain independent fallbacks:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-disable'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disable'
su -c 'sh /storage/emulated/0/Download/ts18-brightness-config.sh disable'
```

`nav-disable` removes only the module-owned media group on reconciliation; stock
controls are never reconstructed. `input-off` leaves the stock touch manager's
region intact. Brightness disable stops project policy rewriting so stock
SystemUI/CarSetting owns subsequent brightness changes.

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

The combined installer refuses to coexist with active legacy IDs. The bundled
migration helper marks only those two exact IDs for normal Magisk removal:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-migrate-magisk-modules.sh'
```

Reboot before installing `ts18_sysui`. The helper does not recursively delete
`/data/adb/modules` or unrelated module state.

## LSPosed/SystemUI crash-loop recovery

Use the already-tested LSPosed safe mode or recovery route to disable package:

```text
au.com.cb.ts18.statusbar.input
```

then reboot. Keep LSPosed scope limited to the main `com.android.systemui`
process; do not add Framework/system_server, XTService, CarSetting, TWCore,
Launcher or other vendor processes as a recovery experiment.

If instability remains, disable `ts18_sysui` and reboot. This returns both
project overlays to stock without changing partitions.

## XTService observer-specific recovery

The XTService layer binds from the already-scoped SystemUI process; it does not
hook or modify `com.tw.service.xt`. Private Binder use is admitted only when the
current installed package/component/action and APK SHA match:

```text
com.tw.service.xt
com.tw.service.xt.CommandService
com.tw.service.xt.CommandService.Bind
341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267
```

Repeated bind/register/query/connection failures open only the process-local
XTService breaker. Breaker cleanup best-effort unregisters the callback, unbinds
the exact service connection and clears module observation state. Restarting
SystemUI/rebooting starts a new process and is the recovery boundary.

If reverse/sleep state or the XTService breaker behaves unexpectedly:

1. disable custom right-nav controls so stock SystemUI alone owns the sidebar;
2. save **TS18 Topway Qualification** / diagnostic state if the diagnostic build
   remains stable;
3. restart SystemUI or reboot using the existing safe route;
4. leave vendor media qualification unused until exact identity/bind/callback
   state is again known.

Do not replace/disable XTService, change its permissions, broaden LSPosed scope,
force reverse/sleep/screen state, or invoke other recovered vendor commands as a
recovery technique.

A known-active reverse/sleep observation becoming unknown after service loss is
intentionally not treated as a fresh inactive state. The module may keep its own
media group suppressed until fresh inactive state is observed; stock navigation
and reverse behaviour remain untouched.

Diagnostic `mediaPre/mediaPlay/mediaPause/mediaNext` calls are manual
qualification only. A successful Binder return is not a reason to enable an
automatic fallback or to claim playback recovery.

## Brightness-specific recovery

The managed logical range remains 1..10. Level 0 is unsupported.

Managed brightness is valid only when both exact binary gates pass:

```text
SystemUI.apk SHA-256
668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f

CarSetting.apk package/hash
com.dofun.carsetting
06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

For this exact CarSetting build, the normal physical actuator is
`Settings.System.SCREEN_BRIGHTNESS` with raw range 30..255. Topway 258 controls
mode; 516 is observation-only for Day/Night slots/effective state.

If brightness reports `ACTION_PENDING`, `NO_258_CALLBACK`,
`SCREEN_BRIGHTNESS_READBACK_MISMATCH`, `AUTO_EFFECTIVE_STATE_UNKNOWN`, a
transport/query/settings error, an exact-binary mismatch or breaker-open:

1. disable brightness only;
2. verify stock SystemUI/CarSetting brightness still works;
3. save the dashboard diagnostic report including requested/observed raw
   `screen_brightness`, 258 state and 516 observation state;
4. reboot SystemUI/Android using the already-proven route before another test.

Do not substitute backlight sysfs, Factory Backlight Current, screen-power
command 33281 or another guessed vendor command. Do not weaken the exact
CarSetting/SystemUI gates.

A 516 callback is not physical convergence proof. Conversely, absence of a 516
callback is not itself a failed physical write; it matters when stock Auto needs
an effective Day/Night observation to choose a managed physical target.

## Right-nav-specific recovery

If the dashboard reports a preflight STOP, leave nav mutation off and retain the
reported direct-child names/host summary. Do not hide, recreate or resize OEM
controls to make room.

A valid production injection requires:

- exact mandatory `navbar_home`, `navbar_back`, `navbar_history`,
  `navbar_volume_plus`, `navbar_volume_reduce` direct children;
- only recognised optional `navbar_guanping` / `navbar_app` when present;
- no unknown or duplicate direct child;
- safe weighted vertical geometry >=56dp;
- existing horizontal strip width >=48dp; and
- exactly one module-owned media group.

Any OEM-control regression is an immediate nav-disable STOP.

## STOP and remain stock when

- installed SystemUI SHA/device/API differs;
- managed brightness CarSetting package/hash differs;
- private XTService use does not match the exact package/component/action/SHA or
  repeatedly fails its bounded bind/callback lifecycle;
- reverse/sleep callback semantics are incompatible or unsafe on the exact unit;
- diagnostic vendor media qualification produces duplicated/unsafe/source-
  dependent behaviour that is not understood;
- an RRO is rejected or renders the wrong resource domain;
- exact touch reflection/coordinate/state preflight fails;
- navbar topology is ambiguous or an OEM function changes;
- one normal tap yields multiple media actions;
- Topway 258 mode cannot be confirmed within the bounded sequence;
- physical `SCREEN_BRIGHTNESS` write/readback does not converge;
- stock Auto effective state is unknown while a managed level is configured;
- SystemUI instability, input lockout or boot/ACC regression appears.

Recovery success means stable stock behaviour through the required
restart/reboot boundary, not merely absence of a visible module control.
