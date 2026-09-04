# Changelog

## Unreleased — exact TS18 nav and CarSetting brightness correction

- retained the physically proven compact top-right collapsed-shade path without
  widening its scope or changing its safety bounds;
- corrected the exact Topway right-nav resource contract from generic
  `home`/`back`/`recent_apps`/`app` assumptions to the supplied SystemUI's
  `navbar_home`, `navbar_back`, `navbar_history`, `navbar_app`,
  `navbar_volume_plus`, `navbar_volume_reduce` and `navbar_guanping` names;
- kept Home, Back, Recents and both volume controls mandatory, the exact
  screen/power and app-slot controls optional when absent, and unknown/duplicate
  direct children as fail-open STOP conditions;
- retained immediate OFF -> ON nav reconciliation, stock-panel visibility
  following, >=56dp projected vertical cells, >=48dp existing horizontal width,
  visible-disabled no-session controls and existing-session-only media dispatch;
- replaced the disproven ordinary 516 physical brightness write path with the
  exact supplied `CarSetting.apk` active actuator,
  `Settings.System.SCREEN_BRIGHTNESS`, while retaining logical managed levels
  1..10 mapped linearly to raw 30..255;
- added an exact CarSetting package/SHA-256 gate in addition to the exact
  SystemUI gate so the recovered physical backend is never applied to an unknown
  privileged binary;
- reproduced the exact observed two-stage Topway mode transaction:
  `write(258,1,<mode>)` followed by `write(258,128)`;
- retained Topway 258 as mode authority and 516 as observation-only state for
  packed Day/Night slots and effective Day/Night, rather than treating 516 as
  physical convergence proof;
- changed physical confirmation to `SCREEN_BRIGHTNESS` readback and separated
  `NO_258_CALLBACK` from `SCREEN_BRIGHTNESS_READBACK_MISMATCH` diagnostics;
- kept stock Auto fail-open: managed Day/Night output is selected only after a
  valid effective Day/Night observation is known;
- expanded dashboard/diagnostic reporting for exact CarSetting compatibility,
  live navbar child names, requested logical/raw brightness, observed raw
  brightness, physical write/read timestamps and both 258 transaction stages;
- added focused mapper/policy/action tests and repository source contracts that
  reject the old navbar IDs and an ordinary three-argument 516 brightness write;
- updated the exact-binary fixtures and governing implementation/installation/
  recovery/validation documentation to match the supplied APK evidence; and
- retained the single user-facing `ts18_sysui` Magisk module containing both RRO
  APKs plus the SystemUI-only LSPosed behavioural module.

Physical success is not inferred from source or CI. The corrected build still
requires the exact-unit staged matrix in `docs/VALIDATION.md` before release
qualification.

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