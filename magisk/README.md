# Magisk component

This template is populated by `tools/package-release.sh` with the built geometry
RRO at:

`system/product/overlay/TS18StatusBarGeometry.apk`

Magisk maps that path systemlessly onto `/product/overlay`. The stock OEM sysbar
RRO remains present and becomes authoritative again when this module is disabled.
