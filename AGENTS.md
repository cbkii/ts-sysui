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
- Hashing must stay off the SystemUI main thread.
- A reflection/resource/topology mismatch means zero exact mutation; do not
  broaden matching or fall back silently.
- Preserve the legacy Xposed bridge unless exact installed LSPosed evidence
  establishes and justifies a migration.

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

- The framework RRO is the only status-bar height authority. Do not reintroduce
  generic window-height normalization.
- The SystemUI visual RRO may override only the allow-list in
  `reference/exact-ts18-systemui-resource-matrix.md`.
- Do not restore recursive View-tree scaling or override status/nav/shared-risk
  resources to make the overlay convenient.
- Overlay/idmap rejection is a STOP that retains stock visuals; it is not a
  reason to replace or resign SystemUI.

## Right-navigation contract

- Recognise only the exact vertical weighted `navbar_left` host with all seven
  known direct OEM functions and no unknown direct child.
- Preserve every stock View instance, ID, listener and `LayoutParams`.
- Controlled proportional reflow is permitted only by inserting one tagged
  module-owned weighted group when measured stock and media cells remain at
  least 56dp. The 48dp floor is not a production fallback.
- Reinflation, disablement and failure cleanup remove only module-owned Views.
- Nav configuration and circuit breaker remain independent from compact status.
- Use only an existing `MediaController` through public `TransportControls`.
  Never create MediaSession/service/queue/notification/audio-focus authority.
- One tap produces at most one command. Do not combine TransportControls with a
  media-key fallback or guessed `TWSystemUI.write(...)` command.

## Change discipline

1. Keep geometry, visuals, compact input and nav independently recoverable.
2. Preserve observation-first, mutation-off defaults across policy generations.
3. Roll back only state whose module ownership is still provable.
4. Treat CI/static evidence as non-physical.
5. Validate staged SystemUI restart, reboot, cold boot and ACC sleep/wake before
   describing a release as physically proven.
6. Follow `docs/EXACT-TS18-SYSTEMUI-FINALISATION.md` and
   `docs/RIGHT-NAV-MEDIA-ROADMAP.md` for STOP conditions and evidence labels.
