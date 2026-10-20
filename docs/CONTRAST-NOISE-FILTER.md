# Targeted SystemUI contrast WTF filtering

The exact API 29 SystemUI emits `ContrastColorUtil` WTF reports for translucent
notification backgrounds. This optional filter prevents only the matching
`Log.wtf(String,String)` call from entering its WTF handler/DropBox path. It does
not change drawing, delete existing entries, disable a DropBox category or touch
unrelated WTFs. Unknown binaries retain stock logging through the existing exact
SystemUI hash gate. Installation failure retains stock logging.

The feature is independently disabled by default. After installing and scoping
the tested module to main `com.android.systemui`, read the previous value:

```sh
su -c 'settings get global ts18_systemui_filter_contrast_wtf'
```

Explicitly enable it:

```sh
su -c 'settings put global ts18_systemui_filter_contrast_wtf 1'
```

To disable immediately, write `0`. For rollback restore the previous value, or
delete this setting if it was absent (`null`). The observer applies changes
without restarting SystemUI. Module disable/restart is the existing recovery
route. No protected APK or partition changes are required.

Qualification: verify the `contrast-noise-hook` installation event and exact hash
acceptance, then reproduce the notification contrast warning. New matching
DropBox reports should stop while an unrelated WTF remains recorded. Test disable
and module/SystemUI restart, reboot and ACC wake. These physical tests are NOT_RUN.
