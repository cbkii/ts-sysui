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
- A fail-open decision must be observable through bounded diagnostics; silently
  doing nothing is not adequate product behaviour when a safe reason can be
  reported.

## Exact contract

- Behavioural mutation requires exact installed `SystemUI.apk` SHA-256
  `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`.
- Hashing must stay off the SystemUI main thread.
- A reflection/resource/topology mismatch means zero exact mutation; do not
  broaden matching or fall back silently.
- Preserve the legacy Xposed bridge unless exact installed LSPosed evidence
  establishes and justifies a migration.
- The signature-protected SystemUI bridge may report CHECKING/BLOCKED states
  before exact identity resolves, but it must reject behavioural mutation until
  the contract is SUPPORTED.

## Collapsed touch contract

Any changed shade/touch region must:

- remain at least 64px from both physical top corners;
- never exceed 20% of full physical width;
- exclude the current right-nav inset;
- apply only after stock Android-Q touch-region computation; and
- retain stock behaviour for ambiguous coordinates/region, expanded shade,
  keyguard/bouncer, heads-up, bubbles and layout transitions.

Configuration can narrow the strip or increase a gap, never weaken these limits.

## Geometry and visuals

- The framework RRO is the only status-bar height authority.
- The SystemUI visual RRO may override only the allow-list in
  `reference/exact-ts18-systemui-resource-matrix.md`.
- Do not restore recursive View-tree scaling or override shared/nav dimensions.
- Overlay/idmap rejection retains stock visuals; it is not a reason to replace
  or resign SystemUI.
- Geometry and visuals remain independent Android RRO projects but the normal
  user-facing installation packages them together in Magisk module `ts18_sysui`.
- Never automatically delete legacy Magisk module state; explicit migration is
  required before the combined module is installed.

## Right-navigation contract

- Recognise only the exact vertical weighted `navbar_left` host.
- Current physical evidence makes Home, Back, Recents, Volume+ and Volume− the
  mandatory direct controls. Exact-static screen/power and app-slot controls are
  known optional/conditional children; any unknown direct child remains a STOP.
- Preserve every stock View instance, ID, listener and `LayoutParams`.
- Controlled proportional reflow is permitted only through one tagged
  module-owned group.
- Production vertical media cells must remain >=56dp. The existing OEM strip
  width must remain >=48dp and is used at full width; 56dp horizontal is
  preferred, not a reason to widen/reject the OEM strip solely for rounding.
- Reinflation, disablement and failure cleanup remove only module-owned Views.
- OFF -> ON configuration must reconcile in-process without requiring a restart
  merely to notice settings.
- Configured controls remain visible-but-disabled when no usable media session
  exists so injection and media authority can be diagnosed independently.
- Nav configuration and circuit breaker remain independent from compact status.
- Use only an existing `MediaController` through public `TransportControls`.
  Never create MediaSession/service/queue/notification/audio-focus authority.
- One tap produces at most one command. Do not combine TransportControls with a
  media-key fallback or guessed `TWSystemUI.write(...)` command.

## Brightness contract

- Semantic authority remains Topway command/callback `516` for separate
  Day/Night slots and command `258` for mode (`0=Auto`, `1=Day`, `2=Night`).
- Do not use Android `screen_brightness`, backlight sysfs or panel calibration as
  the ordinary actuator/fallback.
- Active private brightness mutation requires the exact current SystemUI gate.
- Brightness has its own policy generation, persistent enable switch and
  process-local breaker. Brightness failure must not disable compact/nav.
- Managed level `0` remains unsupported until a timed exact-device recovery test
  proves safe recovery.
- `set_auto` explicitly selects Day/Night by local time; it must not depend on
  stock ILL/headlight Auto.
- Do not issue the first vendor query/write until `TWSystemUI.init()` or a valid
  Topway callback proves transport readiness.
- Policy persistence is not hardware success. Report transport readiness,
  mode/level state, pending action, 258/516 callbacks and semantic confirmation.
- Confirm one pending action callback-first: bounded wait -> one semantic query ->
  at most one controlled retry -> explicit error. Do not hot-loop MCU writes.
- Factory Backlight Current, panel files, `DIM_ADJ`/`LED_PWM`, VCOM/AVDD and
  screen-power command 33281 remain separate protected domains.

## Change discipline

1. Keep compact input, nav and brightness independently recoverable; the two RRO
   APKs share one normal user-facing Magisk module but remain independently
   build/testable.
2. Preserve mutation-off defaults across policy generations.
3. Roll back only state whose module ownership is provable.
4. Treat CI/static evidence as non-physical.
5. Validate staged SystemUI restart, reboot, cold boot and ACC sleep/wake before
   describing a release as physically proven.
6. Follow `docs/PHYSICAL-0.5.1-REMEDIATION.md`,
   `docs/EXACT-TS18-SYSTEMUI-FINALISATION.md`,
   `docs/RIGHT-NAV-MEDIA-ROADMAP.md` and
   `docs/BRIGHTNESS-CONTROLLER.md` for STOP conditions and evidence labels.
