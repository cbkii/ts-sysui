# Architecture

## Controlling layers

### Geometry — framework RRO

The status-bar frame/insets are framework-owned. The exact supplied active
sysbar RRO was exported under the extractor name
`android.overlay.sysbar_720x1280_10.apk`; project runtime evidence associates the
installed overlay with `/product/overlay/framework-res_sysbar_rro_1280x720.apk`.
It targets package `android` and overrides the status/nav geometry resources.

This project follows that OEM mechanism with a new narrow RRO that changes only:

- `status_bar_height`
- `status_bar_height_landscape`
- `status_bar_height_portrait`

No navigation dimensions are changed. The new overlay is mounted by Magisk under
`/product/overlay`; the OEM overlay stays intact.

### Collapsed input — SystemUI process only

Making a `View` return `false` from `onTouchEvent()` is insufficient once Window
Manager has already selected the SystemUI status-bar window as the input target.
The input window's touchable `Region` must exclude the app-facing area.

The LSPosed module uses Android framework surfaces *inside only the SystemUI main
process*:

1. hook `WindowManagerImpl.addView/updateViewLayout` to identify the actual
   `TYPE_STATUS_BAR` root and track its window height;
2. hook `ViewTreeObserver.dispatchOnComputeInternalInsets` **after** stock
   listeners, but act only when the observer belongs to that captured status-bar
   root.

On a clearly collapsed bar, the final region obeys these hard invariants:

```text
cornerGapPx >= 64
stripWidth <= floor(fullStatusBarWidth * 0.20)
stripLeft >= cornerGapPx
stripRight <= fullStatusBarWidth - cornerGapPx
stripRight <= fullStatusBarWidth - rightSystemInset
```

The configured fraction may be smaller than 20%, but never larger. The right
system inset is resolved from current `WindowInsets`, falling back to
`android:navigation_bar_width` for the TS18 side nav bar. A root-set
`ts18_statusbar_right_inset_px` override remains available if the live inset
source proves wrong; `-1` means automatic.

For 1280 px width / 55 px right inset / 64 px corner exclusion, the default is
**x=960..1216**: 256 px wide, exactly 64 px from the physical right corner and 9
px left of the historical navigation boundary.

If the touch region extends below the compact bar (expanded shade, heads-up or
other transient surface), the hook leaves the stock region untouched. Keyguard
is also fail-open/stock. If the hard geometry cannot be represented safely, the
module leaves stock behaviour unchanged rather than reducing the corner gap.

### Visual scale

The current implementation does not require private Topway SystemUI class names.
Collapsed leaf `View`s are uniformly scaled to 75% while their parent
layout/touch containers remain unchanged. Original scales are retained in weak
references and restored when visual scaling is disabled.

An exact future SystemUI resource pass may replace generic leaf scaling only if
static/runtime evidence shows a safer resource contract.

## Right-navigation media controls — future separate feature

The right-side `NavigationBar0` is a separate protected SystemUI surface. It is
not modified by the compact-status-bar v0.2 runtime. A future optional module may
inject bounded Previous / Play-Pause / Next controls only after the roadmap
evidence gates are satisfied. It must reuse the currently active Android
MediaSession/MediaController authority and must not create a new playback
service, MediaSession, queue, audio-focus owner or notification authority.

See [`ROADMAP.md`](ROADMAP.md).

## Failure policy

- Missing framework classes/methods: no hook mutation.
- Any repeated runtime hook exception: after three failures the in-process
  circuit breaker disables all modifications until SystemUI restarts.
- Persistent kill switch: `settings put global ts18_statusbar_enabled 0`.
- Expanded/keyguard/ambiguous touch state: stock region is preserved.
- No `system_server` hook exists in this version.
