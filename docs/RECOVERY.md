# Recovery

The geometry RRO and LSPosed APK are independent. Do not troubleshoot both layers
at once.

## LSPosed/SystemUI problem

Preferred non-destructive recovery order:

1. set the persistent master switch off if Android is usable:
   `settings put global ts18_statusbar_enabled 0`;
2. disable the LSPosed module/scope and restart SystemUI or reboot;
3. if visual scaling had been armed, a process restart guarantees all transient
   view transforms/listeners are discarded;
4. only then investigate logs and current WindowManager/input state.

The in-process breaker also deactivates after three hook failures and attempts to
restore only visual transforms it can still prove it owns. It never claims to
undo unrelated SystemUI animation state.

## Geometry problem

Disable the Magisk module `ts18_statusbar_geometry` and reboot. The OEM RRO is
not deleted or replaced, so it becomes authoritative again.

## STOP conditions

Stop rather than escalating to partition/package replacement if recovery requires
writing `/system` or `/product`, replacing `SystemUI.apk`, deleting the OEM RRO,
or adding `system_server` hooks without new exact-device evidence.
