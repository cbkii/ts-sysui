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
**x=960..1216**.

### Visual scaling — optional/experimental

Visual scaling is **off by default** and must be armed separately after input is
qualified. The optional implementation scales collapsed leaf views while leaving
layout/touch containers unchanged.

Ownership is conservative:

- root location is computed once per traversal rather than once per leaf;
- a leaf leaving the bar is restored immediately if the module still owns its
  scale;
- if SystemUI/animation changes scale while the module owns a leaf, the module
  releases that leaf without overwriting the new value and skips it until visual
  state is reset;
- disabling visuals, replacing the root, or opening the circuit breaker removes
  listeners and restores only transforms that are still provably module-owned.

Exact SystemUI resource/layout overrides remain preferable if a current runtime
hierarchy establishes a safer contract.

## Installation and failure state

Hook registration is idempotent. Registered callbacks stay inert until all
required hooks are installed. If installation fails part-way, already registered
hooks are unhooked and no runtime mutation is armed.

Runtime configuration is observation-only by default:

```text
master enabled = false
input enabled  = false
visual enabled = false
```

After three runtime hook failures, the process circuit breaker deactivates
further mutation, detaches visual listeners, restores transforms still owned by
the module, clears the tracked root, and remains disabled until SystemUI restarts.
The persistent kill switch is `ts18_statusbar_enabled=0`.

No `system_server` hook exists.

## Right-navigation media controls — future separate feature

`NavigationBar0` remains a separate protected SystemUI surface and is unchanged
by this release. Future optional media controls remain evidence-gated and must
reuse an existing MediaSession without adding playback authority. See
[`ROADMAP.md`](ROADMAP.md).
