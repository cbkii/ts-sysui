# Right-navigation media controls roadmap

## 1. Purpose

This document is the implementation plan for adding optional media transport
controls to the exact TS18 right-side SystemUI navigation surface without
regressing the compact status-bar work.

The feature is intentionally evidence-gated. The current repository can safely
build the **observation and policy foundation** before any right-nav view is
changed. A clickable navigation-bar control is not permitted until the exact
current `com.android.systemui` implementation and a fresh on-device hierarchy
capture establish a safe host, free space, lifecycle and command authority.

The target remains CB's exact Topway TS18 running Android 10/API 29. Generic
TS18/TS10 compatibility is not claimed.

## 2. Product contract

### MVP behaviour

Keep the current OEM right-nav controls untouched and add only optional media
controls in genuinely unused space:

```text
existing OEM navigation controls
────────────────────────────────
Previous       optional
Play / Pause   optional
Next           optional
────────────────────────────────
existing OEM/Topway controls
```

The MVP permits the user to:

- enable any subset of `Previous`, `Play/Pause`, and `Next`;
- choose the order of those optional controls;
- show only the configured prefix that safely fits at the minimum production
  touch size.

The MVP must **not** hide, replace, reorder, resize or recreate existing Home,
Back, Recents, volume, vehicle, projection or other OEM controls. Configurability
of existing OEM controls is a later feature and requires separate evidence.

### Touch/layout policy

- preferred production touch-cell height: **56 dp**;
- absolute engineering floor: **48 dp**;
- production MVP must not automatically shrink below 56 dp merely to fit more
  controls;
- no scrolling navigation strip;
- no overlapping touch rectangles;
- no change to the framework right-nav width;
- keep at least a small separation from confirmed stock-control bounds;
- if no safe cell exists, inject nothing.

The historic ~55 px right strip is context only. Runtime dimensions and actual
child bounds are authoritative.

## 3. Authority model

Media buttons are **control clients only**:

```text
SystemUI optional button
  -> Android MediaSessionManager
  -> existing selected MediaController
  -> existing TransportControls
```

The feature must not create a playback service, MediaSession, queue, notification
authority, audio-focus owner, media database or duplicate player state.

A Topway-private command path is out of scope unless public Android media control
is proven insufficient on the exact device and the private contract is recovered
separately.

## 4. Evidence state

### Established baseline

Repository/project evidence supports:

- Android 10/API 29 as the target platform;
- historical 1280x720 physical display geometry;
- a historically observed roughly 55 px right reserved/navigation region;
- `com.android.systemui` as a protected surface;
- a SystemUI-only LSPosed scope;
- framework RRO ownership of status-bar geometry;
- a requirement that right-nav work be optional, reversible and unable to
  disable/regress compact-status-bar behaviour.

### Missing before clickable mutation

The following remain required before a functional button is enabled:

1. exact current `com.android.systemui` APK path, package/version metadata and
   SHA-256;
2. decoded/static mapping of the exact navigation-bar implementation where
   useful;
3. current on-device hierarchy and bounds for the `TYPE_NAVIGATION_BAR` root;
4. proof of genuinely unused space in relevant states;
5. lifecycle/reinflation behaviour sufficient to guarantee exactly-once
   injection and complete removal;
6. proof that SystemUI can obtain/control the intended active MediaController on
   this API 29 build without additional privilege changes.

Plugin/interface APKs are not substitutes for the full runtime SystemUI APK.

## 5. Architecture

The right-nav feature remains inside the existing LSPosed APK and reuses the
already-installed exact `WindowManagerImpl.addView/updateViewLayout` hooks. Do
not install a second broad WindowManager hook.

### `NavBarState`

Responsibilities:

- recognise `TYPE_NAVIGATION_BAR` layout parameters;
- retain only a weak reference to the current root;
- assign a generation when the root is replaced;
- retain current layout width/height specs for diagnostics.

No UI mutation belongs here.

### `NavHierarchyProbe`

Observation-only subsystem:

- attaches only when `ts18_statusbar_nav_probe_enabled=1`;
- records the current navigation root and bounded public View hierarchy data;
- logs only after a meaningful hierarchy/layout change and rate-limits output;
- caps depth, node count, labels and total emitted text;
- detaches cleanly on root replacement, probe disable or nav feature breaker;
- never changes visibility, layout params, touchability, click listeners or
  content.

Capture fields include:

```text
root generation
root class and physical bounds
display size and density
layout-param width/height specs
child path and class
resource package/type/entry name when resolvable
bounds relative to nav root and physical screen
visibility
clickable / long-clickable / enabled / focusable
bounded contentDescription
layout-param class
child count
```

### `NavConfig`

Independent policy generation and settings:

```text
ts18_statusbar_nav_policy_version=1
ts18_statusbar_nav_enabled=0
ts18_statusbar_nav_probe_enabled=0
ts18_statusbar_nav_actions=previous,play_pause,next
ts18_statusbar_nav_min_touch_dp=56
ts18_statusbar_nav_debug=0
```

`nav_enabled` is reserved for the future mutation stage and must remain false in
the observation release.

Malformed action configuration fails closed rather than inventing an order.

### `NavFeatureRuntime`

The navbar has a **separate process-local circuit breaker**. Three right-nav
feature failures disable only right-nav observation/mutation until SystemUI
restarts.

A navbar failure must not call the compact status-bar circuit breaker or disable
the status-bar input/visual runtime.

### `NavLayoutPolicy`

Pure/JVM-testable placement policy. Inputs are measured navbar height, occupied
stock intervals, configured action count, touch-cell size and gaps. Output is
either safe slot rectangles or an explicit failure reason.

Required failure vocabulary:

```text
NO_NAV_ROOT
ROOT_NOT_STABLE
AMBIGUOUS_HIERARCHY
NO_CONFIRMED_HOST
NO_SAFE_FREE_SPACE
TARGET_TOO_SMALL
STOCK_OVERLAP
UNSUPPORTED_REINFLATION
```

The first implementation chooses a contiguous free region and returns as many
configured controls as fit at the production target size. It never solves a
capacity problem by overlapping stock controls.

### Future `NavInjectionController`

Not active in the observation release.

When evidence gates are satisfied it will:

- add exactly one uniquely tagged owned container;
- add/remove only views whose ownership is provable;
- never change stock child layout params;
- recompute after known root reinflation;
- remove all owned views immediately when disabled or safety becomes ambiguous.

### Future `NavMediaSessionRepository`

Not active in the observation release.

When enabled it will maintain a bounded selected existing `MediaController`
without blocking scans on the SystemUI main thread. A single button tap must
dispatch exactly one transport action; do not simultaneously send
`TransportControls` and media-key fallback commands.

## 6. Delivery phases

### Phase 0 — hardened baseline

**Status: complete before this feature branch.**

- merge the v0.3 runtime/CI/release hardening;
- resolve review findings;
- require green build/unit/lint/package checks;
- retain all physical-validation disclaimers.

### Phase 1 — observation foundation

**Safe to implement now. No right-nav mutation.**

Deliver:

- `TYPE_NAVIGATION_BAR` root tracking through existing WindowManager hooks;
- independent nav configuration and policy generation;
- bounded hierarchy probe;
- independent nav feature circuit breaker;
- action parser/order policy;
- pure layout-capacity policy and JVM tests;
- root helper commands for `nav-observe`, status, probe disable, action order and
  touch target configuration;
- documentation and validation instructions.

Acceptance:

- CI green;
- nav settings default off;
- no code path adds, removes, resizes or makes a right-nav view clickable;
- compact status-bar tests remain green;
- physical status explicitly remains unverified.

### Phase 2 — exact-device observation qualification

**Requires physical TS18 access.**

1. re-check Android build, Magisk/Zygisk/LSPosed and active SystemUI writers;
2. obtain and hash the current full `com.android.systemui` APK;
3. enter `nav-observe`;
4. capture the navigation hierarchy in:
   - launcher/home;
   - ordinary app;
   - IME visible;
   - immersive/fullscreen;
   - keyguard;
   - reverse camera;
   - active phone call;
   - projection, where applicable;
5. repeat across:
   - SystemUI restart;
   - launcher restart;
   - reboot;
   - cold boot;
   - ACC sleep/wake.

Exit criteria:

- exact root/host/lifecycle is unambiguous;
- stock controls and their occupied bounds are understood;
- a genuinely unused region exists without displacing stock controls;
- no duplicate or alternate nav root makes ownership ambiguous.

Otherwise STOP at observation-only.

### Phase 3 — inert marker

**Evidence-gated.**

Add one uniquely owned, **non-clickable** marker to one confirmed unused slot.

Verify:

- no stock view moves or changes measured bounds;
- no stock touch target changes;
- marker is added exactly once;
- disable/root replacement removes it completely;
- special vehicle/UI states remain safe.

Failure returns the feature to observation-only.

### Phase 4 — Play/Pause only

**Evidence-gated.**

Add one clickable Play/Pause control only after:

- SystemUI media-control authority has been proven;
- active-session selection is deterministic enough for the target use;
- inert marker lifecycle is clean.

Dispatch one `play()` or `pause()` operation based on reliable current playback
state. If state is ambiguous, use a conservative documented policy rather than
issuing multiple fallback commands.

### Phase 5 — Previous/Next and configured order

After exactly-once Play/Pause behaviour is proven:

- add `skipToPrevious()` and `skipToNext()`;
- enable the configured action subset/order;
- expose only the prefix that fits at the 56 dp production target;
- keep all stock controls unchanged.

### Phase 6 — state-aware presentation

Optional after callback lifecycle is proven:

- update Play/Pause artwork from bounded playback-state callbacks;
- unregister callbacks on controller/root changes;
- rate-limit/log only meaningful session transitions.

A static combined Play/Pause presentation is preferable to unreliable state.

### Phase 7 — optional UX/native integration

Only after the functional path is stable:

- consider a settings Activity with toggles and drag-to-reorder;
- inspect whether the exact TS18 SystemUI retains a safe Android-10
  `NavigationBarInflaterView`/vertical-layout extension contract;
- prefer a proven native/vendor extension path over manual injection only if it
  preserves every stock Topway control and rollback guarantee.

## 7. Validation matrix

Every mutation phase must cover, as applicable:

| State | Stock controls unchanged | New group safe | Media command exactly once |
|---|---|---|---|
| Launcher/home | required | required | required |
| Ordinary app | required | required | required |
| IME visible | required | required | required |
| Immersive/fullscreen | required | required | required |
| Keyguard | required | fail-open allowed | not required |
| Reverse camera | required | fail-open preferred | not required |
| Phone call | required | fail-open preferred | conditional |
| Projection | required | fail-open preferred | conditional |

Lifecycle acceptance must include SystemUI restart, launcher restart, reboot,
cold boot and ACC sleep/wake before physical qualification is claimed.

## 8. Recovery and STOP conditions

The feature must remain separately disableable from compact status-bar input and
visual settings.

STOP and leave the right nav stock if any of these occurs:

- current SystemUI identity cannot be established;
- navigation host/root is ambiguous;
- free space cannot be proven;
- an injected marker/control moves or obscures a stock control;
- a root lifecycle can duplicate owned views;
- minimum touch target cannot be met;
- active MediaController authority or selection is unreliable;
- one tap can dispatch duplicate commands;
- rollback/removal cannot be guaranteed;
- reverse-camera/call/projection behaviour is not understood sufficiently to
  preserve stock safety.

Do not replace the whole navbar, hook `system_server`, alter global density,
change the framework nav width or infer Topway private commands merely to bypass
one of these gates.

## 9. Definition of done

### Observation milestone

- detailed roadmap committed;
- right-nav root tracking is read-only;
- bounded hierarchy capture is independently armed;
- pure action/layout policies have regression tests;
- nav failures cannot disable compact status-bar runtime;
- CI is green;
- exact-device physical status remains clearly unverified.

### Functional MVP

- exact current SystemUI APK/path/hash recorded;
- current hierarchy/lifecycle evidence retained;
- stock controls remain byte-for-byte/behaviourally owned by OEM code and
  geometrically unchanged;
- optional Previous/Play-Pause/Next subset/order works through an existing
  MediaSession;
- all enabled controls meet production touch-size policy;
- exactly-once command behaviour is proven;
- disable/reinflation restores a stock-only navbar;
- physical validation matrix and lifecycle boundaries pass.

Until those functional-MVP criteria are met, the repository must describe the
right-nav work as **observation/evidence preparation**, not as working media
controls.
