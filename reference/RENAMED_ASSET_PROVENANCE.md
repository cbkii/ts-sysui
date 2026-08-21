# Renamed supplied APK provenance

The source APK exports use application/package-oriented names and are not
necessarily the basenames used on the read-only Android partitions.

## Corrected mapping

Per the supplied TS18 project provenance:

| Exported filename | Original/installed role |
|---|---|
| `Android System_10.apk` | extracted `/system/framework/framework-res.apk` resource package (`android`); the export label is application-facing, not the partition basename |
| `android.overlay.sysbar_720x1280_10.apk` | extracted active TS18 sysbar resource overlay associated with `/product/overlay/framework-res_sysbar_rro_1280x720.apk` |

This resolves the v0.1.0 error where the second APK was treated as only a sibling
orientation overlay because its exported package/filename contains `720x1280`.
The installed-path evidence uses `framework-res_sysbar_rro_1280x720.apk`; the
exporter name is not authoritative for the original partition basename.

## SystemUI identity

`com.android.systemui.plugins_10.apk` decodes as package
`com.android.systemui.plugins`; it is a plugin-interface APK and must not be
renamed conceptually to `SystemUI.apk` merely because the string contains
`systemui`.

The later exact export `System UI_10.apk` establishes package
`com.android.systemui`, installed role `/system/priv-app/SystemUI/SystemUI.apk`
and SHA-256
`668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`.
That exact binary, not the plugin-interface name, is the static authority for the
private API29 touch and Topway navbar fixtures. Runtime use still requires a
fresh installed-APK hash match.
