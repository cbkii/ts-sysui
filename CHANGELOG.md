# Changelog

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
- strengthened the circuit breaker to deactivate mutation, detach visual listeners
  and restore only transforms still provably owned by the module;
- restricted touch-region mutation to a recognised full-width collapsed REGION
  state and added live physical/window coordinate validation;
- allowed configured touch widths from 1% through the hard 20% maximum while
  retaining the >=64 px physical corner exclusion and right-inset exclusion;
- hardened optional visual scaling against moved/reused views, per-axis external
  scale conflicts and rollback failures, and removed per-leaf root-location allocations;
- cached framework dimension resolution/configuration values on SystemUI hot paths;
- added stage-bounded error logging with first-failure stack traces;
- expanded pure/JUnit policy coverage and added legacy Xposed/APK contract checks;
- centralised version metadata in `version.properties`;
- pinned Gradle 8.9 and added verified official wrapper-JAR bootstrap rather than
  trusting a mismatched repository binary;
- added tracked-source manifest verification and regeneration tooling;
- added Android Lint to CI and hardened/pinned build/release actions;
- hardened release signing-key lifetime, tag/version checks, signature verification,
  SDK-root resolution and interrupted-script failure handling;
- updated installation/recovery/validation docs for staged arming and legacy Xposed compatibility.

## 0.2.0

- corrected supplied APK provenance mapping;
- implemented 43 dp status-bar geometry RRO and hard-bounded collapsed input strip;
- added optional 0.75 generic visual leaf scaling;
- added initial CI, packaging, recovery and validation documentation.
