# TS18 System UI engineering policy

This repository targets CB's exact Topway TS18 Android 10/API 29 unit. Never
claim generic TS10/TS18 compatibility or Android 16 support.

## Authority and safety

- Fresh exact-device evidence outranks decoded/static evidence, which outranks
  analogous FYT/UIS implementations.
- Treat `com.android.systemui` and framework overlays as protected surfaces.
- Keep every change systemless, reversible, independently disableable and
  fail-open.
- Never replace `SystemUI.apk`, delete OEM overlays, write `/system` or
  `/product`, change platform signing, or hook `system_server` without a new
  approved exact-device design.
- Root does not imply platform signing, UID 1000, signature permission or SELinux
  authority.
- Scope LSPosed only to the main `com.android.systemui` process and API 29.
- Do not commit proprietary APKs, decoded firmware, signing material or captured
  user/media data.

## Exact contract

- Behavioural mutation requires the exact installed `SystemUI.apk` SHA-256
  `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`.
- Brightness mutation additionally depends on the exact supplied/current
  `CarSetting.apk` actuator contract, SHA-256
  `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71`;
  a changed CarSetting binary blocks managed brightness until re-analysed.
- Private XTService Binder use additionally requires the current installed
  `com.tw.service.xt` APK to hash to the supplied exact
  `341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267`.
  Historical package/version/path evidence does not replace this live hash gate.
- XTService use is limited to read-only reverse/sleep observation and explicit
  diagnostic media qualification described in
  `reference/exact-ts18-xtservice-contract.json`. Do not copy or expose the full
  vendor command surface merely because it exists in the supplied APK.
- Hashing must stay off the SystemUI main thread; callbacks that touch View or
  SystemUI lifecycle state must be marshalled explicitly back to the main looper.
- A reflection/resource/topology mismatch means zero exact mutation; do not
  broaden matching or fall back silently.
- Preserve the legacy Xposed bridge unless exact installed LSPosed evidence
  establishes and justifies a migration.

## Collapsed touch contract

Any changed shade/touch region must:

- remain at least 64px from both physical top corners;
- never exceed 20% of full physical width;
- exclude the current right-nav inset;
- apply through one module-owned Android-Q internal-insets listener ordered after
  stock lifecycle changes; and
- retain stock behaviour for ambiguous coordinates/region, expanded shade,
  keyguard/bouncer, heads-up, bubbles and layout transitions.

The exact adapter must runtime/type-check the Android-Q
`StatusBarTouchableRegionManager.mShouldAdjustInsets` ownership signal. When it
is true, leave `InternalInsetsInfo` completely stock. In the proven ordinary
collapsed state, exact mode may explicitly convert the default FRAME/empty state
to `TOUCHABLE_INSETS_REGION` and set only the bounded strip. Configuration can
narrow the strip or increase a gap, never weaken these limits.

## Geometry and visuals

- The framework RRO is the only status-bar height authority. Do not reintroduce
  generic window-height normalization.
- The SystemUI visual RRO may override only the allow-list in
  `reference/exact-ts18-systemui-resource-matrix.md`.
- Do not restore recursive View-tree scaling or override status/nav/shared-risk
  resources to make the overlay convenient.
- Overlay/idmap rejection is a STOP that retains stock visuals; it is not a
  reason to replace or resign SystemUI.

## Right-navigation contract

- Recognise only the exact vertical weighted `navbar_left` host.
- The exact supplied `SystemUI.apk` names the required physical controls
  `navbar_home`, `navbar_back`, `navbar_history`, `navbar_volume_plus`, and
  `navbar_volume_reduce`; `navbar_guanping` and `navbar_app` are known optional
  decoded children. Do not regress to generic AOSP `home`, `back`,
  `recent_apps`, or `app` resource names.
- Current physical evidence makes Home, Back, Recents and both volume controls
  mandatory. Any unknown direct child remains a STOP, and diagnostics must report
  the live direct-child resource entry names before guessing at a firmware drift.
- Preserve every stock View instance, ID, listener and `LayoutParams` and retain
  the current runtime order rather than assuming one historical firmware order.
- The module is subordinate to stock Topway navigation visibility. Never force
  the panel visible. If the stock root/`navbar_left` is detached, hidden or not
  shown (including reverse/fullscreen-style states), remove/suspend module-owned
  controls and stop media observation; when stock returns, run the full exact
  topology and measurement preflight again.
- Exact XTService reverse/sleep callbacks are an additional module-only veto, not
  a replacement for public View visibility. Active reverse/sleep removes only
  module-owned controls. Fresh inactive/wake state must rerun the complete exact
  preflight before reinjection. Never command reverse, sleep, screen power, MCU,
  CAN or OEM navigation visibility.
- Read-only observation of `navigationbar_config`, `show_navigationbar` and
  `persist.navibar.position` may invalidate module-owned cached/preflight state.
  Never write those values or treat historical values as current runtime truth.
- Controlled proportional reflow is permitted only by inserting one tagged
  module-owned weighted group when projected vertical cells remain at least
  56dp. The existing OEM strip width is retained; 48dp is the absolute
  horizontal floor and never a licence to narrow or widen the stock strip.
- Reinflation, disablement and failure cleanup remove only module-owned Views.
- Nav configuration and circuit breaker remain independent from compact status.
- Normal runtime media authority remains only an existing `MediaController`
  through public `TransportControls`. Never create MediaSession/service/queue/
  notification/audio-focus authority.
- XTService `mediaPre/mediaPlay/mediaPause/mediaNext` are diagnostic qualification
  calls only. They are never an automatic fallback and are never combined with a
  normal `MediaController` dispatch.
- One tap produces at most one command. Do not combine TransportControls with a
  media-key fallback or guessed `TWSystemUI.write(...)` command.

## Brightness contract

- Exact supplied `CarSetting.apk` static evidence supersedes the earlier
  assumption that Topway command `516` is the ordinary physical brightness
  actuator on this build.
- The active CarSetting slider writes
  `Settings.System.SCREEN_BRIGHTNESS`, using the physical raw range `30..255`.
  The module keeps its managed logical range `1..10` and maps it monotonically to
  that raw range (`1 -> 30`, `10 -> 255`).
- Command/callback `516` remains observation-only for this module: it can expose
  effective Day/Night and packed Topway slots, but a 516 callback must never be
  used as physical brightness-write confirmation.
- Topway command `258` remains the mode authority. Reproduce the exact observed
  CarSetting mode transaction on the main looper: `write(258,1,<mode>)` followed
  by `write(258,128)`. Do not invent an unsupported semantic label for the
  second stock operation.
- Re-read `Settings.System.SCREEN_BRIGHTNESS` after a managed physical write and
  require convergence before reporting success. A returned/persisted policy
  acknowledgement is not physical confirmation.
- Active brightness mutation requires the exact SystemUI hash and exact
  CarSetting hash gates. Unknown/changed binaries fail open; do not weaken either
  check.
- Brightness has its own policy generation, persistent enable switch and
  process-local circuit breaker. A brightness failure must not disable compact
  status-bar or right-nav functionality.
- All brightness mutation defaults off. Managed logical level `0` remains
  unsupported until a timed exact-device no-backlight recovery test proves safe
  recovery.
- `set_auto` is a local-clock policy that explicitly selects Day or Night; it
  must not depend on the stock ILL/headlight Auto decision.
- Stock `Auto` keeps Topway's own mode decision authoritative. If module-managed
  Day/Night physical levels are enabled in stock Auto, select the applicable
  level only after a valid 516 observation establishes effective Day/Night;
  otherwise fail open rather than guess.
- Do not issue the first vendor 258 query/write until stock `TWSystemUI.init()`
  has completed or a valid Topway callback directly proves the transport is live.
- Confirmation is bounded: physical level writes use Settings readback; mode
  changes use the observed 258 callback/state; query before retry, cap attempts,
  and open only the brightness breaker on non-convergence. Breaker cleanup must
  remove owned observers/receivers/workers without touching stock state.
- Diagnostic writer attribution must distinguish proof from temporal correlation.
  A `SCREEN_BRIGHTNESS` observer event may be correlated with module writes,
  Topway 258/516 callbacks or stock Topway writes within a bounded window, but it
  must be labelled external/unknown when causality is not established.
- Do not add backlight sysfs, panel calibration, Factory Backlight Current,
  `DIM_ADJ`/`LED_PWM`, VCOM/AVDD, screen-power command `33281`, a CarSetting
  LSPosed scope, or a second physical brightness authority without new evidence.

## Diagnostic and development builds

- Follow `docs/DIAGNOSTIC-BUILD-POLICY.md` for every new SystemUI-facing runtime
  feature, hook, bridge, build variant and diagnostic export.
- Emit the module-entry event before risky hook installation. Each required or
  optional hook-install stage must have a stable stage name and explicit
  installing/success/failure state so all-or-nothing rollback is diagnosable.
- `debug` and `diagnostic` builds force bounded verbose diagnostics. `release`
  keeps conservative rate limits but retains structural state transitions.
- Diagnostic output must use safe sinks: Android logcat tag `TS18SysUI`, the
  LSPosed/Xposed log where available, and the bounded in-process journal.
- The normal APK process must remain safe if Xposed classes are absent.
- Diagnostic logging must be bounded, non-blocking and free of synchronous shell,
  package-manager CLI, Binder-heavy probing or filesystem scans on UI/render/touch
  hot paths.
- Do not log media titles, user text, credentials, tokens, contacts, messages or
  unrelated application/file data.
- Mounted RRO files are not proof of effective overlays; diagnostic builds should
  expose resource values resolved by the live framework/SystemUI process.
- Brightness diagnostics must keep Topway semantic observation separate from the
  physical `SCREEN_BRIGHTNESS` write/readback path and expose both 258 transaction
  stages.
- The diagnostic-only Topway qualification UI may invoke exactly one explicit
  XTService media method per user action after both exact identity gates pass.
  Binder success is not playback confirmation and automatic vendor fallback
  remains prohibited.
- The release-derived `diagnostic` variant keeps the production application ID.
  A trusted Manual Diagnostic Build may use the configured release certificate
  solely to permit exact-device upgrade testing. It must never tag, publish a
  GitHub Release or push source metadata.
- PR CI must compile/lint diagnostic variants and enforce that release builds
  cannot accidentally enable forced diagnostic logging or the diagnostic
  launcher.

## Change discipline

1. Keep geometry, visuals, compact input, nav, XTService observation and
   brightness independently recoverable.
2. Preserve observation-first, mutation-off defaults across policy generations.
3. Roll back only state whose module ownership is still provable.
4. Treat CI/static evidence as non-physical.
5. Validate staged SystemUI restart, reboot, cold boot and ACC sleep/wake before
   describing a release as physically proven.
6. Follow `docs/EXACT-TS18-SYSTEMUI-FINALISATION.md`,
   `docs/RIGHT-NAV-MEDIA-ROADMAP.md`, `docs/BRIGHTNESS-CONTROLLER.md`,
   `docs/DIAGNOSTIC-BUILD-POLICY.md`,
   `docs/EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md`,
   `docs/EXACT-XTSERVICE-VEHICLE-OBSERVATION.md` and the current
   physical-remediation runbook for STOP conditions and evidence labels.
