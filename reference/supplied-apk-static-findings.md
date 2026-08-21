# Supplied APK static findings

## High-value files

The repository design starts from APKs supplied from the exact TS18 project
corpus, not from a generic phone/SystemUI assumption.

### `System UI_10.apk`

- SHA-256: `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`;
- installed role: `/system/priv-app/SystemUI/SystemUI.apk`;
- package/version: `com.android.systemui`, Android 10/versionCode 29;
- shared UID: `android.uid.systemui`;
- platform signer SHA-256:
  `AA:6F:9F:B3:07:05:12:AC:96:24:25:79:7C:D6:5A:A5:85:CF:62:02:93:7E:E3:CE:EF:B1:4B:58:02:EA:BD:F3`;
- exact static analysis establishes Android-Q
  `StatusBarTouchableRegionManager`, Topway `NavigationBarView` additions, the
  weighted `navbar_left` host and `MEDIA_CONTENT_CONTROL` authority;
- private member/resource use remains gated by the machine-readable fixture and
  a fresh installed-APK hash check.

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

Use Topway's exact supplied framework-RRO mechanism for height. Prefer the
verified exact SystemUI touch-region and navbar contracts over broad framework
hooks, while keeping every mutating private adapter off after an identity/member
mismatch. Plugin interfaces and exporter filenames remain insufficient evidence
for private implementation details.
