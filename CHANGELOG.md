# Changelog

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
