# Recovery and rollback

## Fast LSPosed kill switch

If root shell is available:

```sh
su -c 'settings put global ts18_statusbar_enabled 0'
```

Then restart SystemUI only if your existing TS18 recovery procedure makes that
safe; otherwise reboot normally. The hook checks this key and fails open.

If SystemUI repeatedly faults before you can run that command, disable the
`TS18 Status Bar Input` module from LSPosed/safe mode and reboot.

## Geometry rollback

Disable `TS18 Compact Status Bar Geometry` in Magisk and reboot. Because the
module only adds a systemless RRO, the untouched OEM sysbar overlay becomes
active again.

## Full uninstall order

1. Disable/uninstall the LSPosed module; reboot and confirm stock touch behaviour.
2. Disable/uninstall the Magisk geometry module; reboot and confirm stock height.
3. Optionally remove `ts18_statusbar_*` global settings after stock behaviour is
   confirmed.

No direct `/system` or `/product` cleanup should be necessary.
