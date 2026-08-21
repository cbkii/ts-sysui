# Recovery

Geometry, visuals, compact input and right-nav media are independently
disableable. Recover the narrowest affected layer first.

## Behavioural recovery while Android is usable

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-disable'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-off'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh disable'
```

Restart SystemUI using the unit's proven method or reboot. `nav-disable` removes
only the owned media group on reconciliation; stock controls are never restored
from guessed/reconstructed copies. `input-off` leaves the stock touch manager's
region intact.

Use `nav-reset` to reset generation-2 nav settings off, and `disarm` to clear
compact generation-4 mutation flags. These do not disable Magisk modules.

## Visual or geometry recovery

- For icon/clock visual problems, disable Magisk module
  `ts18_statusbar_visuals` only.
- For status height/inset problems, disable `ts18_statusbar_geometry`.
- Reboot and verify `cmd overlay list --user 0` plus the validator report.

Do not delete an OEM overlay, replace SystemUI, change the product partition or
alter signing to repair an RRO.

## LSPosed/SystemUI crash-loop recovery

Use the already-tested LSPosed safe mode or recovery/adb route to disable module
package `au.com.cb.ts18.statusbar.input`, then reboot. If needed, disable both
project Magisk modules without touching OEM modules.

Once stable, collect bounded logs and exact contract output before re-enabling
one layer at a time. A process-local compact/nav breaker resets on SystemUI
restart, but persistent settings remain; keep them explicitly off during
diagnosis.

## STOP and remain stock when

- installed SystemUI SHA/device/API differs;
- visual or geometry idmap is rejected;
- exact touch reflection/coordinate/state preflight fails;
- the nav host has an unknown/missing child, nonuniform weights or projected
  cell below 56dp;
- any OEM nav function changes;
- one tap yields multiple media actions;
- SystemUI instability, input lockout or boot/ACC regression appears.

Recovery success is stock behaviour plus stable restart/reboot, not merely the
absence of a visible module control.
