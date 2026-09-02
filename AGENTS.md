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

- Recognise only the exact vertical weighted `navbar_left` host. Current physical
  evidence makes Home, Back, Recents and both volume controls mandatory; the
  exact-static screen/power and app-slot controls are known optional children.
  Any unknown direct child remains a STOP.
- Preserve every stock View instance, ID, listener and `LayoutParams` and retain
  the current runtime order rather than assuming one historical firmware order.
- The module is subordinate to stock Topway navigation visibility. Never force
  the panel visible. If the stock root/`navbar_left` is detached, hidden or not
  shown (including reverse/fullscreen-style states), remove/suspend module-owned
  controls and stop media observation; when stock returns, run the full exact
  topology and measurement preflight again.
- Controlled proportional reflow is permitted only by inserting one tagged
  module-owned weighted group when projected vertical cells remain at least
  56dp. The existing OEM strip width is retained; 48dp is the absolute
  horizontal floor and never a licence to narrow or widen the stock strip.
- Reinflation, disablement and failure cleanup remove only module-owned Views.
- Nav configuration and circuit breaker remain independent from compact status.
- Use only an existing `MediaController` through public `TransportControls`.
  Never create MediaSession/service/queue/notification/audio-focus authority.
- One tap produces at most one command. Do not combine TransportControls with a
  media-key fallback or guessed `TWSystemUI.write(...)` command.

## Brightness contract

- The current exact-device semantic authority is Topway command/callback `516`
  for separate Day/Night 0–10 slots and command `258` for mode. The recovered
  current mode values are `0=Auto`, `1=Day`, `2=Night`.
- Do not use Android `screen_brightness`, backlight sysfs or panel calibration as
  the ordinary brightness controller.
- Active private brightness mutation requires the same exact current SystemUI
  SHA-256 gate. Unknown/changed binaries fail open; do not weaken the hash check.
- Brightness has its own policy generation, persistent enable switch and
  process-local circuit breaker. A brightness failure must not disable compact
  status-bar or right-nav functionality.
- All brightness mutation defaults off. Managed level `0` remains unsupported
  until a timed exact-device no-backlight recovery test proves safe recovery.
- `set_auto` is a local-clock policy that explicitly selects Day or Night; it
  must not depend on the stock ILL/headlight Auto decision.
- Do not issue the first vendor brightness query/write until stock
  `TWSystemUI.init()` has completed or a valid Topway callback directly proves
  the existing transport is live.
- Confirmation is callback-first and bounded: query before any retry, cap write
  attempts, and open only the brightness breaker on non-convergence. Breaker
  cleanup must remove owned observers/receivers/workers without touching stock
  Topway state.
- Factory Backlight Current limits, panel files, `DIM_ADJ`/`LED_PWM`, VCOM/AVDD
  and screen-power command `33281` are separate protected domains.

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
- The release-derived `diagnostic` variant keeps the production application ID.
  A trusted Manual Diagnostic Build may use the configured release certificate
  solely to permit exact-device upgrade testing. It must never tag, publish a
  GitHub Release or push source metadata.
- PR CI must compile/lint diagnostic variants and enforce that release builds
  cannot accidentally enable forced diagnostic logging or the diagnostic
  launcher.

## Change discipline

1. Keep geometry, visuals, compact input, nav and brightness independently
   recoverable.
2. Preserve observation-first, mutation-off defaults across policy generations.
3. Roll back only state whose module ownership is still provable.
4. Treat CI/static evidence as non-physical.
5. Validate staged SystemUI restart, reboot, cold boot and ACC sleep/wake before
   describing a release as physically proven.
6. Follow `docs/EXACT-TS18-SYSTEMUI-FINALISATION.md`,
   `docs/RIGHT-NAV-MEDIA-ROADMAP.md`, `docs/BRIGHTNESS-CONTROLLER.md`,
   `docs/DIAGNOSTIC-BUILD-POLICY.md` and the current physical-remediation
   runbook for STOP conditions and evidence labels.
