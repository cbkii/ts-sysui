# Architecture

## Layer ownership

| Layer | Authority | Artifact | Independent recovery |
|---|---|---|---|
| status geometry | Android framework resources | geometry Magisk RRO | disable `ts18_statusbar_geometry` |
| status visuals | exact SystemUI resources | visual Magisk RRO | disable `ts18_statusbar_visuals` |
| collapsed input | exact SystemUI touch manager | LSPosed APK | `input-off` / compact kill switch |
| right-nav media | exact Topway navbar + existing media sessions | LSPosed APK | `nav-disable` / nav breaker |

No layer rewrites another layer's authority. In particular, the LSPosed code
does not change status-window height or framework navigation dimensions.

## Exact identity gate

`ExactSystemUiIdentity` hooks `SystemUIApplication.onCreate`, resolves the
installed application `sourceDir` and computes SHA-256 on a daemon worker. Its
state is `UNCHECKED`, `CHECKING`, `SUPPORTED` or `UNSUPPORTED`. Exact touch and
nav paths require `SUPPORTED`; a read/reflection mismatch leaves them inert.

The current contract is API 29, device/product token `s9863a1h10`, package
`com.android.systemui` and APK SHA-256
`668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`.
The root pre-arm script verifies the same values before publishing an enabled
setting.

## Geometry RRO

The exact OEM sysbar overlay uses framework geometry resources. The project
follows that mechanism and changes only:

- `status_bar_height`;
- `status_bar_height_landscape`; and
- `status_bar_height_portrait`.

Each becomes 43dp. `navigation_bar_*` and `navigation_key_width` are untouched.

## SystemUI visual RRO

The independent static overlay targets `com.android.systemui` and changes only:

- `status_bar_icon_size = 18dp`;
- `status_bar_icon_drawing_size = 13dp`; and
- `status_bar_clock_size = 10.5sp`.

These names are the exact static-analysis allow-list. The overlay does not own
height, padding, navigation dimensions or shared resources. Android 10
overlay/idmap acceptance is intentionally a device qualification gate. Failure
retains stock visual resources; no code traversal compensates for it.

## Exact collapsed touch adapter

The Android-Q `StatusBarTouchableRegionManager` is the touch authority. The
adapter reflection-preflights its exact constructor, methods and fields, then
hooks after stock:

- constructor `(Context, HeadsUpManagerPhone, StatusBar, View)`;
- `updateTouchableRegion()`; and
- `onComputeInternalInsets(InternalInsetsInfo)`.

Because the hidden `OnComputeInternalInsetsListener` is not in public SDK stubs,
the module attaches a runtime-reflected proxy to the manager's exact root. The
stock method hook and owned listener are idempotent; cleanup unregisters only
the owned proxy.

Mutation requires exact identity, armed compact generation 4/exact adapter, an
attached full-physical-width root at `(0,0)`, ordinary rectangular full-width
stock region and matching compact height. The safety policy rejects expanded,
keyguard/bouncer, pinned/departing HUN, bubble and force-collapsed-transition
states. It then computes:

```text
cornerGapPx >= 64
stripWidth <= floor(fullPhysicalWidth * 0.20)
stripLeft >= cornerGapPx
stripRight <= fullPhysicalWidth - cornerGapPx
stripRight <= fullPhysicalWidth - rightSystemInset
```

The explicitly selected compatibility adapter remains isolated in the older
WindowManager/dispatch observation path. It is never an automatic fallback.

## Exact Topway right-nav adapter

`ExactTopwayNavAdapter` hooks `NavigationBarView.onFinishInflate` after stock.
`ExactTopwayNavController` owns attach/detach/layout/reinflation reconciliation.
It resolves `navbar_left` in the SystemUI resource namespace and accepts only:

- direct vertical `LinearLayout` host;
- the exact seven direct IDs for power, Home, Back, Recents, app slot, volume up
  and volume down;
- no explicit host weight sum and no unknown direct child;
- visible stock children with height `0` and uniform positive weights; and
- a live measured cell projection of at least 56dp.

One tagged, generated-ID group is inserted before the two stock volume controls.
Its outer weight is `stockUnitWeight * actionCount`; enabled action children
have inner weight 1. Stock children are not edited. The owner tag plus retained
reference makes exactly-once injection and ownership-bounded removal explicit.

Nav generation 2 remains mutation/probe off by default. Probe and functional
state share the exact lifecycle root but the bounded probe is independently
armed.

## Existing-session media client

`NavMediaSessionRepository` uses public API29 surfaces available inside the
already-authorised SystemUI process. A dedicated `HandlerThread` registers an
active-session listener, chooses a sticky controller deterministically and
registers one callback on the selected controller. UI snapshots return to the
main handler.

Selection priority is sticky playing controller, first playing controller,
sticky usable controller, first paused controller, then first usable controller.
A click posts once to the worker; pure dispatch policy maps it to at most one
supported previous/play/pause/next `TransportControls` call. There is no media
key, vendor command or second playback authority.

Detach/disable stops the repository, unregisters listener/callback, drops main
callbacks and quits the worker safely.

## Failure isolation and defaults

Hook registration is transactional. Required hook failure unhooks partial work
before `HookRuntime` activates. Exact adapter preflight failure installs no
mutation for that adapter.

Compact and nav failures have independent three-strike process breakers. The
compact breaker detaches exact touch listeners and clears compact state. The nav
breaker removes the owned group, stops media observation, detaches the probe and
clears nav state. Neither breaker edits persistent settings; a SystemUI restart
resets only process-local state.

Persistent defaults are compact generation 4 and nav generation 2 with all
mutation flags off. No `system_server` hook exists.
