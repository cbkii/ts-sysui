# Build and validation status

## Current source state

The current remediation branch builds on merged PR #5 (exact SystemUI touch,
visuals and right-nav media) and merged PR #4 (Topway brightness). Canonical
release metadata on `main` is v0.5.1/versionCode 6; this branch is an unreleased
follow-up and does not change that version merely to record source fixes.

Implemented in source:

- exact-device SystemUI contract fixture and installed-APK verifier;
- non-blocking APK hashing with all resolved lifecycle/View callbacks dispatched
  explicitly on the SystemUI main looper;
- one exact Android-Q `StatusBarTouchableRegionManager` mutation listener;
- runtime/type-checked `mShouldAdjustInsets` stock-ownership gate: special stock
  states remain untouched, while safe ordinary collapsed computation explicitly
  establishes `TOUCHABLE_INSETS_REGION` from the default FRAME/empty state;
- hard 20% full-width maximum, 64px physical corner exclusions and current
  right-nav inset exclusion;
- independent framework geometry and exact-SystemUI visual RROs;
- exact Topway `NavigationBarView/navbar_left` weighted media group with current
  required/optional stock-child contract, measured sizing and exactly-one public
  `MediaController.TransportControls` dispatch;
- stock-nav visibility monitor: hidden/detached/not-shown Topway panels suspend
  module controls and media observation; a visible return runs the full preflight
  again without reverse/MCU hooks or forced visibility;
- exact Topway brightness 258/516 policy with Auto/Day/Night/Set-auto, managed or
  preserved Day/Night slots, live callback/confirmation diagnostics, safe 1..10
  managed range and independent breaker;
- brightness breaker cleanup that removes module-owned time receiver, settings
  observer, queued work and worker thread while leaving stock Topway state alone;
- unified TS18 System UI dashboard/control bridge plus root recovery helpers;
- host policies, JUnit, exact-source contracts, Lint/build/package/signature
  workflows, proprietary-artifact exclusion and source-manifest validation.

All protected runtime mutations remain explicitly opt-in/fail-open.

## Latest repository verification

The remediation branch must be considered **source-pending** until the final
post-change GitHub Actions Build completes successfully. Inspect the final
workflow logs, not only the check badge, for host contract tests, JVM tests,
Android compile/lint, APK assembly/contract and package validation.

Once that final run is green, the correct label is **CI-validated**, not
physically qualified.

## Physical status

The first exact-device v0.5.1 installation is current contrary evidence for the
old source assumptions: Day/Night produced no visible brightness difference and
right-nav media buttons did not appear. The remediation branch adds observability
and bounded fixes but does not erase those physical results.

The following remain unverified on the remediated exact TS18 until new evidence
is recorded: overlay/idmap acceptance and rendered dimensions; corrected exact
touch routing; right-nav host visibility, measurement/reflow and media-session
behaviour; brightness 258/516 convergence; reverse/DVR/fullscreen coexistence;
keyguard/HUN/bubbles; calls/projection; SystemUI and launcher restart; reboot,
cold boot and ACC sleep/wake; and long-duration stability.

Use `PHYSICAL-0.5.1-REMEDIATION.md` and `VALIDATION.md` in staged order. A failed
runtime reflection check for `mShouldAdjustInsets`, an unknown navbar topology,
overlay rejection or non-converging brightness state is a clean STOP, not a
reason to broaden the exact contract.
