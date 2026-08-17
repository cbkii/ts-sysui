# Supplied APK static findings

## High-value files

The repository design starts from APKs supplied from the exact TS18 project
corpus, not from a generic phone/SystemUI assumption.

### `android.overlay.sysbar_720x1280_10.apk`

- SHA-256: `93a51dd6f10605191607ae76d6b05ad175ac1b58473550c6955520c0203a9767`
- resource-only APK: no `classes.dex`;
- overlay package: `android.overlay.sysbar_720x1280`;
- target: framework package `android`;
- overlay priority: `5`;
- supplied provenance identifies this export as the active TS18 sysbar RRO
  associated with `/product/overlay/framework-res_sysbar_rro_1280x720.apk`;
- resource names include:
  - `status_bar_height`
  - `status_bar_height_landscape`
  - `status_bar_height_portrait`
  - `navigation_bar_height`
  - `navigation_bar_height_landscape`
  - `navigation_bar_frame_height`
  - `navigation_bar_frame_height_landscape`
  - `navigation_bar_width`
- the three status-bar dimensions encode `58dp`;
At the last-observed TS18 density override of 153 dpi, 58 dp is about 55.5 px,
which directly matches the historical ~55 px top status-bar region closely.

### `Android System_10.apk`

- SHA-256: `c2beb858e2bb2dc5b1debad0c09ac87e239ee10a3b0fe22670912ae04a8ae566`
- decoded package: `android`;
- framework resources/manifest context;
- no `classes.dex`;
- supplied provenance maps it to `/system/framework/framework-res.apk`; the exporter filename is not its original partition basename.

### SystemUI plugin APKs

`com.android.systemui.plugins_10.apk` and
`com.android.systemui.plugin.testoverlayplugin_10.apk` are not the full runtime
SystemUI implementation. They are not a basis for private Topway status-bar or
navigation-bar class hooks.

## Design consequence

Use Topway's exact supplied framework-RRO mechanism for height, but keep input
control on Android framework window/insets surfaces inside `com.android.systemui`
until a decoded full SystemUI implementation is explicitly mapped. Do not invent
private Topway class names from plugin interfaces or exported filenames.
