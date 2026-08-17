# Architecture

## Controlling layers

### Geometry — framework RRO

Status-bar frame/insets are framework-owned. The exact supplied active sysbar
RRO was exported under the extractor name `android.overlay.sysbar_720x1280_10.apk`;
project evidence associates the installed overlay with
`/product/overlay/framework-res_sysbar_rro_1280x720.apk`. It targets `android`
and carries the status/navigation geometry resources.

This project follows the OEM mechanism with a narrow systemless RRO that changes
only:

- `status_bar_height`
- `status_bar_height_landscape`
- `status_bar_height_portrait`

The right navigation dimensions are not changed. No SystemUI hook attempts to
normalise the status-bar window height; the framework resource layer is the sole
owner of geometry unless future physical evidence proves otherwise.

### Collapsed input — SystemUI process only

Returning `false` from a status-bar child view is insufficient once WindowManager
has already selected the SystemUI window as the input target. The input window's
computed touchable `Region` must exclude the app-facing area.

The legacy Xposed module uses framework surfaces only inside the main
`com.android.systemui` process:

1. `WindowManagerImpl.addView/updateViewLayout` identifies and tracks the actual
   `TYPE_STATUS_BAR` root after successful stock calls;
2. `ViewTreeObserver.dispatchOnComputeInternalInsets` is observed **after** stock
   listeners and only when the observer belongs to that captured root;
3. the stock region is modified only when a pure safety policy identifies a
   full-width, rectangular, region-mode, collapsed bar with a matching compact
   window height;
4. keyguard, empty/non-rectangular regions, heads-up-like regions, expanded or
   ambiguous states remain stock.

Before applying physical corner requirements, the module verifies that the
status-bar window is located at physical `(0,0)` and that its local width exactly
matches the real display width. A partial or offset window fails open.

On an eligible collapsed bar, the final strip obeys:

```text
cornerGapPx >= 64
stripWidth <= floor(fullPhysicalWidth * 0.20)
stripLeft >= cornerGapPx
stripRight <= fullPhysicalWidth - cornerGapPx
stripRight <= fullPhysicalWidth - rightSystemInset
```

Configured width may be **1%–20%**; only the 20% maximum is a product limit. The
right inset comes from current `WindowInsets` with an Android framework
`navigation_bar_width` fallback. A root-only override remains available if live
evidence proves that automatic inset source wrong.

For 1280 px width / 55 px right inset / 64 px corner exclusion, 20% resolves to
half-open **[960,1216)**.

### Visual scaling — optional/experimental

Visual scaling is **off by default** and must be armed separately after input is
qualified. The optional implementation scales collapsed leaf views while leaving
layout/touch containers unchanged.

Ownership is conservative:

- root location is computed once per traversal rather than once per leaf;
- a leaf leaving the bar is restored immediately if the module still owns its
  scale;
- if SystemUI/animation changes one scale axis while the module owns a leaf, the
  unchanged module-owned axis is restored before ownership is released;
- disabling visuals, replacing the root, or opening the circuit breaker removes
  listeners and restores only transforms that are still provably module-owned.

Exact SystemUI resource/layout overrides remain preferable if a current runtime
hierarchy establishes a safer contract.

### Right-navigation observation — SystemUI process only

v0.4 adds **read-only** right-nav observation to the same exact
`WindowManagerImpl.addView/updateViewLayout` hook registrations. It recognises
`TYPE_NAVIGATION_BAR`, tracks the root weakly and assigns a generation on
replacement.

`NavHierarchyProbe` is independently armed and bounded. It records public View
metadata and geometry but never changes visibility, layout params, click
listeners, touchability or hierarchy contents. Probe listeners are removed on
root replacement, explicit disable or the right-nav feature breaker.

Navbar configuration uses a separate policy generation:

```text
ts18_statusbar_nav_policy_version=1
ts18_statusbar_nav_enabled=0
ts18_statusbar_nav_probe_enabled=0
ts18_statusbar_nav_actions=previous,play_pause,next
ts18_statusbar_nav_min_touch_dp=56
ts18_statusbar_nav_debug=0
```

`nav_enabled` is reserved; the observation milestone contains no navbar mutation
path.

`NavLayoutPolicy` is pure code. It converts measured stock occupied intervals
into safe candidate slots at the requested touch size. Its output is not applied
until later evidence gates are satisfied.

### Right-navigation failure isolation

Navbar observation/mutation has its own process-local `NavFeatureRuntime`
breaker. Three navbar feature failures detach navbar observation and disable only
that feature until SystemUI restarts.

Navbar failures do **not** call the compact status-bar `CircuitBreaker`. Shared
hook-installation failure can still fail the whole module open before runtime
activation.

The future media-control authority is deliberately not implemented yet. When
evidence-gated functional controls are added, they must act only as clients of an
existing Android MediaSession/MediaController. See
[`RIGHT-NAV-MEDIA-ROADMAP.md`](RIGHT-NAV-MEDIA-ROADMAP.md).

## Installation and failure state

Hook registration is idempotent. Registered callbacks stay inert until all
required hooks are installed. If installation fails part-way, already registered
hooks are unhooked and no runtime mutation is armed.

Runtime configuration is observation-only by default:

```text
status master enabled = false
status input enabled  = false
status visual enabled = false
right-nav probe       = false
right-nav mutation    = false
```

After three compact status-bar runtime failures, the compact process circuit
breaker deactivates status-bar mutation, detaches visual listeners, restores
owned transforms and clears the tracked status root until SystemUI restarts.
The persistent compact kill switch is `ts18_statusbar_enabled=0`.

No `system_server` hook exists.
