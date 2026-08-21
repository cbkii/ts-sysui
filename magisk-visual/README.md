# Independent SystemUI visual Magisk component

`tools/package-release.sh` places the built SystemUI visual RRO at:

`system/product/overlay/TS18StatusBarVisuals.apk`

This module is independent from framework geometry. It targets only the
allow-listed exact SystemUI status resources and can be disabled without
removing the 43 dp geometry module or LSPosed behaviour.

Current overlay/idmap acceptance remains a physical validation gate. Rejection
must leave stock visuals; do not replace `SystemUI.apk` or change platform
signing to bypass it.
