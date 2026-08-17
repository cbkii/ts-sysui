# Recovery

The geometry RRO, compact SystemUI runtime and right-nav observation state are
separate concerns. Do not troubleshoot multiple layers at once.

## Compact LSPosed/SystemUI problem

Preferred non-destructive recovery order:

1. set the persistent compact master switch off if Android is usable:
   `settings put global ts18_statusbar_enabled 0`;
2. disable the LSPosed module/scope and restart SystemUI or reboot;
3. if visual scaling had been armed, a process restart guarantees all transient
   view transforms/listeners are discarded;
4. only then investigate logs and current WindowManager/input state.

The compact in-process breaker deactivates after three hook failures and attempts
to restore only visual transforms it can still prove it owns. It never claims to
undo unrelated SystemUI animation state.

## Right-nav observation problem

v0.4 right-nav code is read-only, but its probe is independently disableable:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
```

To return all navbar settings to safe defaults:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-reset'
```

The navbar has a separate process-local feature breaker. Three navbar
observation/future-nav failures detach its listeners and disable only navbar
work until SystemUI restarts. It must not deactivate compact status-bar input or
visual state.

If SystemUI remains unstable after navbar settings are reset, disable the whole
LSPosed module and reboot before drawing conclusions about the cause.

## Geometry problem

Disable the Magisk module `ts18_statusbar_geometry` and reboot. The OEM RRO is
not deleted or replaced, so it becomes authoritative again.

## STOP conditions

Stop rather than escalating to partition/package replacement if recovery requires
writing `/system` or `/product`, replacing `SystemUI.apk`, deleting the OEM RRO,
altering right-nav framework dimensions, or adding `system_server` hooks without
new exact-device evidence.
