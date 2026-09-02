# Architecture

## Layer ownership

| Layer | Authority | Artifact | Independent recovery |
|---|---|---|---|
| status geometry | Android framework resources | geometry RRO inside `ts18_sysui` | disable combined Magisk module |
| status visuals | exact SystemUI resources | visual RRO inside `ts18_sysui` | disable combined Magisk module |
| collapsed input | exact Android-Q SystemUI touch manager | LSPosed APK | compact kill switch / LSPosed disable |
| right-nav media | exact Topway navbar + existing media sessions | LSPosed APK | nav disable / nav breaker |
| brightness | Topway 258 mode + exact CarSetting `SCREEN_BRIGHTNESS` physical path | LSPosed APK | brightness disable / brightness breaker |

The two RRO projects remain independently built/tested but are packaged together
for installation. Behavioural layers remain independently disableable. No layer
rewrites another layer's authority.

## Exact binary gates

`ExactSystemUiIdentity` verifies the installed SystemUI source asynchronously and
requires the exact API/device/package/hash contract before private SystemUI
mutation:

```text
API 29
s9863a1h10
com.android.systemui
/system/priv-app/SystemUI/SystemUI.apk
SHA-256 668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f
```

Callbacks that can touch Views/SystemUI lifecycle state are marshalled back to
the main looper.

The CarSetting-derived managed brightness backend has an additional exact gate:

```text
com.dofun.carsetting
SHA-256 06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71
```

A binary/class/member/resource/topology mismatch fails open. Compatibility is not
silently broadened.

## Geometry and visual RROs

The framework RRO is the sole status-bar-height authority and changes only:

- `status_bar_height`;
- `status_bar_height_landscape`; and
- `status_bar_height_portrait`.

Each becomes 43dp. Navigation dimensions are untouched.

The exact-SystemUI visual RRO changes only the reviewed allow-list:

- `status_bar_icon_size = 18dp`;
- `status_bar_icon_drawing_size = 13dp`; and
- `status_bar_clock_size = 10.5sp`.

Overlay/idmap rejection is a clean STOP; runtime View-tree scaling is not used as
a fallback.

## Exact collapsed touch adapter

The Android-Q `StatusBarTouchableRegionManager` remains stock touch authority.
The exact adapter reflection/type-checks its required private contract, including
boolean `mShouldAdjustInsets`.

One persistent module-owned post-stock `OnComputeInternalInsetsListener` owns the
exact mutation path. Constructor/`updateTouchableRegion()` lifecycle hooks only
keep that listener correctly attached/ordered; the project does not reintroduce
a second direct `onComputeInternalInsets()` mutation hook.

If `mShouldAdjustInsets` is true or a special/ambiguous state is active, the
module leaves `InternalInsetsInfo` untouched. In safe ordinary collapsed state it
may explicitly establish `TOUCHABLE_INSETS_REGION` from Android-Q's normal
FRAME/empty starting state and constrain the strip by runtime geometry:

```text
cornerGapPx >= 64
stripWidth <= floor(fullPhysicalWidth * 0.20)
stripLeft >= cornerGapPx
stripRight <= fullPhysicalWidth - cornerGapPx
stripRight <= fullPhysicalWidth - rightSystemInset
```

The explicit compatibility adapter remains diagnostic-only and is never an
automatic fallback after exact failure.

## Exact Topway right navigation

`ExactTopwayNavAdapter` hooks `NavigationBarView.onFinishInflate` after stock and
binds only the exact vertical `com.android.systemui:id/navbar_left` host.

Required direct stock resource entry names:

```text
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce
```

Known optional direct names:

```text
navbar_guanping
navbar_app
```

Unknown/duplicate direct children, unsafe weights or unsafe measured geometry are
STOP conditions. Current runtime order, every OEM View/ID/listener/LayoutParams
and Topway command remain untouched.

One owner-tagged weighted group may contain a unique configured subset/order of:

```text
previous,play_pause,next
```

It uses the existing full sidebar width, requires >=48dp horizontal width and
>=56dp projected vertical cells, and never widens the OEM strip. A public-View
visibility monitor removes/suspends module controls whenever stock hides,
detaches or stops showing the panel, then reruns full exact preflight when stock
returns.

## Existing-session media client

Media authority is only:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

`NavMediaSessionRepository` observes active sessions on a dedicated HandlerThread,
selects one controller deterministically/stickily while valid, and publishes UI
state on the main thread. One accepted tap yields at most one supported previous,
play, pause or next transport operation.

No MediaSession, service, queue, notification, audio-focus owner, media-key
fallback or guessed Topway media command is created.

## Exact CarSetting-backed brightness

The supplied CarSetting build establishes two separate authority domains:

- **Topway 258** — semantic mode (`0=Auto`, `1=Day`, `2=Night`);
- **`Settings.System.SCREEN_BRIGHTNESS`** — active physical slider output,
  raw 30..255.

A mode change reproduces the exact observed stock transaction on the SystemUI
main looper after transport readiness:

```text
write(258, 1, selectedMode)
write(258, 128)
```

The private meaning of the second stock operation is not guessed.

Topway 516 is retained as observation-only input for packed Day/Night slots and
the effective Day/Night state. It is not the ordinary physical actuator and is
not physical success evidence.

The user-facing logical range remains 1..10, mapped linearly to raw 30..255.
Level 0 is blocked. `-1` preserves the corresponding configured physical level.

The reconciliation engine changes one required variable at a time. It confirms
258 through mode callback/state and physical output through exact raw
`SCREEN_BRIGHTNESS` readback. On non-convergence it performs one matching
query/read before at most one bounded retry, then records a brightness-only
breaker failure.

Stock Auto remains Topway authority. If managed Day/Night levels are configured,
the physical target is chosen only when a valid 516 effective-state observation
is known; unknown state fails open.

The brightness breaker stops future project writes and cleans up only its owned
receiver, settings observer, queued work and HandlerThread. It does not change
stock Topway state.

## Dashboard and bridge

The companion **TS18 System UI** Activity communicates with a
signature-protected, package-targeted dynamic bridge inside the already
privileged exact SystemUI process. Mutating requests require a private
`ResultReceiver`, exact identity and valid complete policy.

Status reports keep semantic and physical state separate, including navbar live
child names/preflight, media selection, 258/516 observation, exact CarSetting
compatibility, requested logical/raw brightness, observed raw brightness,
physical write/read timestamps, mode transaction stages and independent breaker
state.

Policy persistence acknowledgement never means hardware success.

## Failure isolation and defaults

Compact, nav and brightness have independent process-local failure domains.
Persistent behavioural mutation defaults off. Exact failure does not activate a
generic fallback or modify another feature domain.

The implementation never replaces/resigns SystemUI, writes Android partitions,
hooks `system_server`, broadens LSPosed beyond main `com.android.systemui`, or
uses backlight sysfs/factory panel calibration/screen-power commands as automatic
brightness fallbacks.