# Changelog

## Unreleased — physical 0.5.1 remediation and usability

- recorded the first exact-device 0.5.1 failures before implementation: Day/Night
  produced no visible brightness change and right-nav media buttons were absent;
- added one signature-protected bidirectional SystemUI control/status bridge so
  the companion UI can report exact identity, hook, nav-preflight, media and
  Topway brightness state instead of silently failing open;
- turned the launcher Activity into a TS18 System UI dashboard for compact touch,
  configurable right-nav media actions, brightness controls and bounded
  diagnostics export;
- corrected exact collapsed touch so one post-stock listener owns mutation,
  runtime/type-checks `mShouldAdjustInsets`, leaves stock special states untouched
  and explicitly establishes REGION from ordinary Android-Q FRAME/empty state;
- marshalled exact-SystemUI identity completion callbacks onto the main looper
  before View/SystemUI lifecycle reconciliation;
- made right-nav OFF -> ON configuration invalidate its cache and request
  immediate in-process reconciliation instead of requiring a SystemUI restart;
- retained exact host safety while allowing the two known conditional OEM
  screen/app controls to be absent and still requiring Home, Back, Recents and
  both volume controls plus no unknown direct child;
- added a stock-navigation visibility monitor: hidden/detached/not-shown
  `NavigationBarView/navbar_left` state removes the owned media group and stops
  media observation, while return to visible reruns full exact preflight without
  forcing OEM visibility or hooking reverse/MCU commands;
- separated navbar sizing into a >=56dp vertical production target and a 48dp
  absolute horizontal floor, using the existing OEM strip width rather than
  widening/rejecting it solely for density rounding;
- made injected media controls remain visible-but-disabled without a usable
  MediaController and exposed controller count/package/state/action diagnostics;
- replaced brightness persistence-only success semantics with live transport,
  258/516 callback, detected Day/Night level, pending-action and confirmation
  status;
- replaced the 450ms repeated-write loop with callback-first bounded confirmation,
  one semantic query before retry, controlled retry delay and distinct
  `NO_258_CALLBACK` / `NO_516_CALLBACK` failure reasons;
- made the brightness breaker clean up the module-owned time receiver, settings
  observer, queued work and worker thread while leaving stock Topway state alone;
- added Day/Night equal-slot warnings, explicit Test Day/Test Night flows and a
  bounded restore action in the dashboard while retaining the 1..10 safety gate;
- combined geometry and visual RROs into one normal user-facing `ts18_sysui`
  Magisk module, with an explicit legacy-module migration helper instead of
  deleting `/data/adb` state automatically;
- incorporated secondary 4PDA Topway SystemUI/navigation-panel precedent while
  keeping exact TS18 runtime/binary evidence authoritative, including the
  reverse+DVR panel-hide firmware history and the separation between Android
  navbar geometry and Topway panel behaviour;
- updated packaging/CI contracts for the single Magisk ZIP plus LSPosed APK and
  expanded exact-touch/nav-visibility remediation regression tests.

Physical success is not inferred from source or CI. The repaired build still
requires the exact-unit progression in `docs/PHYSICAL-0.5.1-REMEDIATION.md` and
`docs/VALIDATION.md` before release qualification.

## 0.5.0 — exact TS18 SystemUI finalisation and brightness controller

- recorded the complete exact-device implementation roadmap and machine-readable
  Android 10/API29 SystemUI contract, with installed-APK verification and
  asynchronous SHA-256 gating before private mutation;
- replaced the broad collapsed touch path with an after-stock exact
  `StatusBarTouchableRegionManager` adapter and retained an explicitly selected
  compatibility mode for diagnosis;
- replaced recursive View scaling with an independently packaged, three-resource
  allow-listed SystemUI visual RRO;
- promoted right-nav observation to an exact `NavigationBarView/navbar_left`
  implementation that preserves all seven OEM children and inserts one owned
  weighted media group only after a measured >=56dp preflight;
- added deterministic existing-session controller selection and exactly-one
  `TransportControls` dispatch without a second playback authority or vendor/key
  fallback;
- added an evidence-gated brightness controller using the recovered current
  Topway `258` mode and `516` Day/Night brightness command/callback contracts;
- added Auto, Day, Night and **Set auto** brightness modes, with Set auto using
  user-selected local transition times to select Day/Night explicitly rather
  than relying on the stock Auto/ILL decision;
- added optional managed Day/Night levels from 1..10 while preserving unmanaged
  stock slots; level 0 remains blocked pending physical timed-recovery proof;
- added an independent brightness circuit breaker, exact-SystemUI compatibility
  gate, stock-transport lifecycle gate, bounded state queries and one-variable-
  at-a-time reconciliation;
- added a launcher brightness Activity whose privileged configuration request is
  sender-signature-permission protected and whose acknowledgement returns through
  a private `ResultReceiver` Binder callback; the bounded root helper remains a
  fallback;
- expanded pure/JUnit, Android, source-contract, proprietary-artifact and
  packaging checks while retaining separate geometry, visual and LSPosed
  deliverables;
- corrected architecture, install/recovery, evidence and staged validation docs.

Source/static/CI success does not establish physical TS18 touch, nav, brightness,
reboot, cold-boot or ACC behaviour; those stages remain explicitly unverified
until exercised on the exact unit.

## 0.4.0 — right-nav observation foundation

- added read-only `TYPE_NAVIGATION_BAR` root tracking through the existing exact
  SystemUI WindowManager hooks;
- added an independently armed, bounded right-nav hierarchy probe with weak-root
  lifecycle handling and rate-limited change logging;
- added a right-nav-specific circuit breaker so probe/future nav failures cannot
  disable the compact status-bar runtime;
- added safe parsing for configurable Previous / Play-Pause / Next subset/order;
- added pure/JUnit-tested right-nav free-space/capacity placement policy with
  explicit fail-open reasons and minimum touch-target enforcement;
- added independent navbar policy-generation/settings and root helper commands;
- added a detailed evidence-gated functional media-controls roadmap and physical
  observation validation stage;
- added `ts18-right-nav-evidence.sh` to copy/hash the current SystemUI APK and
  collect bounded package/window/display/probe evidence on the exact device;
- included configuration, validation and right-nav evidence helpers in packaged
  bundles;
- kept all right-nav mutation disabled: no views are added/removed/resized and no
  media command path exists in this milestone.

## 0.3.0 — review hardening

- made LSPosed first-run observation-only: master/input/visual mutations default off;
- removed the speculative SystemUI window-height normaliser so framework RRO is
  the sole geometry authority;
- made hook registration idempotent and partial installation rollbackable, and replaced broad method-name hooks with exact API29 signatures;
- strengthened the circuit breaker to deactivate mutation, detach visual listeners and restore only transforms still provably owned by the module;
- restricted touch-region mutation to a recognised full-width collapsed REGION state and added live physical/window coordinate validation;
- allowed configured touch widths from 1% through the hard 20% maximum while retaining the >=64 px physical corner exclusion and right-inset exclusion;
- hardened optional visual scaling against moved/reused views, per-axis external scale conflicts and rollback failures, and removed per-leaf root-location allocations;
- cached framework dimension resolution/configuration values on SystemUI hot paths;
- added stage-bounded error logging with first-failure stack traces;
- expanded pure/JUnit policy coverage and added legacy Xposed/APK contract checks;
- centralised version metadata in `version.properties`;
- pinned Gradle 8.9 and added verified official wrapper-JAR bootstrap rather than trusting a mismatched repository binary;
- added tracked-source manifest verification and regeneration tooling;
- added Android Lint to CI and hardened/pinned build/release actions;
- hardened release signing-key lifetime, tag/version checks, signature verification, SDK-root resolution and interrupted-script failure handling;
- updated installation/recovery/validation docs for staged arming and legacy Xposed compatibility.

## 0.2.0

- corrected supplied APK provenance mapping;
- implemented 43 dp status-bar geometry RRO and hard-bounded collapsed input strip;
- added optional 0.75 generic visual leaf scaling;
- added initial CI, packaging, recovery and validation documentation.
