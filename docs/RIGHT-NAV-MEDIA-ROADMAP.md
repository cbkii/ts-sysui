# Exact TS18 right-navigation media controls

## Product outcome

Optionally add Previous, Play/Pause and Next to the exact TS18 right-side
SystemUI navigation strip while preserving every OEM function. The feature is
independently recoverable and remains a client of existing media authority only.

The first physical 0.5.1 installation showed **no custom controls at all**. That
current result supersedes the earlier assumption that source/static completion
was enough to reach the exact host at runtime.

## Controlling evidence

The exact API29 SystemUI APK establishes:

- Topway-extended `NavigationBarView.onFinishInflate`;
- vertical weighted `com.android.systemui:id/navbar_left`;
- known stock IDs for screen/power, Home, Back, Recents, app slot, volume up and
  volume down;
- existing Topway ownership of stock commands;
- `android.permission.MEDIA_CONTENT_CONTROL`; and
- Android `MediaSessionManager`/`MediaController` use.

Current physical observation establishes the visibly present controls as Home,
Recents, Back, Volume+ and Volume−. The screen/power and app-slot IDs remain
known exact-static controls but can be conditional/absent at runtime.

## Runtime host contract

The exact after-stock lifecycle hook accepts only
`NavigationBarView.onFinishInflate` from the supported SystemUI binary.

Before mutation, runtime preflight requires:

- exact supported APK identity;
- stock navigation root attached, visible and shown;
- direct vertical `navbar_left` host visible and shown;
- Home, Back, Recents, Volume+ and Volume− present as direct stock children;
- any present screen/power or app-slot child to remain a recognised direct OEM
  child;
- no unknown or duplicate direct child;
- each present stock child to retain weighted `height=0` layout;
- uniform positive visible stock weights;
- no explicit host weight sum;
- non-zero host geometry; and
- production-safe sizing.

The two known optional controls are **not** replaced or guessed when absent. An
unknown child remains an immediate STOP.

One owner-tagged vertical group is inserted before the two stock volume buttons.
Its outer weight equals `stockUnitWeight * actionCount`; each media child has
weight 1. No OEM `LayoutParams`, ID, listener or View instance is edited.
Removal therefore restores the stock-only weight distribution simply by
removing the one owned group.

## Stock visibility and vehicle-state lifecycle

Module media controls are subordinate to stock Topway panel visibility. A
separate public-View lifecycle monitor observes the exact `NavigationBarView`
and `navbar_left`; it never hooks MCU/reverse commands and never calls
`setVisibility()` on stock UI.

When the stock root or host is detached, hidden or not shown, the controller
removes its owned group and stops its `MediaController` observer. When the stock
panel becomes visible again, the controller starts from a fresh exact identity,
topology and measurement preflight before reinjection. This deliberately covers
reverse/fullscreen-style hiding without pretending to know which private vendor
command caused it.

Secondary Topway firmware evidence supports this conservative model: 4PDA
firmware changelogs describe navigation-panel sorting/configuration/fullscreen
changes and, for the December-2024 TS18 branch family, a fix where the panel did
not hide correctly during reverse while DVR was active. Those posts are useful
precedent, not exact current runtime proof:

- `https://4pda.to/forum/index.php?showtopic=1015856&st=28740`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=45340`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=44300`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=46140`

Android navbar geometry remains a separate layer. 4PDA experiments with
`com.android.internal.systemui.navbar.*` overlays demonstrate that reserved
system geometry can change without proving anything about Topway button
semantics. Diagnostics may report such overlays; this project must not enable or
disable foreign navbar overlays automatically.

TS10, Driving and UIS7862/FYT SystemUI variants may be used only for structural
differential research. They are not install/flash candidates for this exact
TS18.

## Physical sizing policy

The 0.5.1 policy applied the same 56dp target to width and height. On this exact
unit the OEM sidebar itself is historically around 55px wide at the 153dpi
override, so density/padding rounding could reject a full-width module control
even when it occupies exactly the same horizontal interaction surface as the
stock buttons.

The remediated policy is:

- **vertical production target: >=56dp**;
- **horizontal hard floor: >=48dp**;
- **horizontal preferred target: >=56dp**;
- use the **entire existing OEM host width** for every media cell;
- never narrow a module cell relative to the current host;
- never widen the navigation strip merely to satisfy a dp rounding target;
- never scroll or overlap controls;
- insufficient projected vertical height remains a hard STOP.

The dashboard reports measured host width/height/density, projected vertical
cell size, horizontal floor/preferred target and whether the preferred width is
met.

## Activation and observability

All mutation still defaults off for a new policy generation, but normal user
activation no longer depends on hidden root shell settings followed by a
SystemUI restart.

The **TS18 System UI** dashboard sends a coherent, signature-protected request to
the injected SystemUI bridge. On nav apply, SystemUI:

1. disables mutation while writing the coherent nav policy;
2. publishes generation/actions/size/debug state;
3. arms enable last;
4. invalidates the nav configuration cache; and
5. immediately calls `requestReconcile()` on the current exact nav binding.

Armed-only polling remains a resilience fallback, not the activation mechanism.

The dashboard exposes:

```text
nav hook installed
root seen/attached
navbar_left host seen
preflight reason
host width/height/density
projected cell size
horizontal floor/preferred state
recognised stock child summary
injected actions
nav breaker state/failure count
media controller count/selected package/state/action bits
```

A hidden stock panel is also emitted as a bounded SystemUI log transition. The
module must not defeat that state merely to keep media buttons visible.

Fail-open without a visible reason is no longer considered sufficient product
behaviour.

## Media authority

`NavMediaSessionRepository` remains on a dedicated HandlerThread:

1. observe active sessions with `MediaSessionManager`;
2. deterministically keep/select one usable existing `MediaController`;
3. observe playback state/capabilities;
4. publish presentation state on the main thread; and
5. map each accepted click to at most one `TransportControls` command.

The feature never creates a MediaSession, service, queue, notification or audio
focus and never combines TransportControls with a media-key/vendor fallback.

The status snapshot includes active-controller count, selected package,
playback state and advertised action bits. This diagnostics information is
bounded and contains no track title/library/user data.

## Presentation

Buttons use module-owned vectors, generated runtime IDs and an ownership tag.
Unsupported commands are disabled and visually de-emphasised.

Crucially, **absence of a usable media session no longer permits the module group
to disappear** while the stock panel itself is visible. Once exact host
injection succeeds, enabled configured controls remain visible but disabled
until the selected MediaController advertises the necessary action. This lets
physical testing distinguish navbar injection from media-session availability.

If stock Topway hides the panel itself, the entire module group follows stock and
is removed/suspended as described above.

Play/Pause artwork/content description follows the selected playback state.
Disabled clicks dispatch nothing.

## Configuration

Generation 2 persists:

```text
ts18_statusbar_nav_policy_version=2
ts18_statusbar_nav_enabled=0|1
ts18_statusbar_nav_probe_enabled=0|1
ts18_statusbar_nav_actions=<unique subset/order of previous,play_pause,next>
ts18_statusbar_nav_min_touch_dp=56
ts18_statusbar_nav_debug=0|1
```

The ordinary product path is the dashboard. The root helper remains an
engineering/recovery fallback.

## Physical progression

After installing the remediated build:

1. Open TS18 System UI and inspect exact identity/nav hook/root/preflight status.
2. If preflight is blocked, save the diagnostics before changing anything.
3. Enable **Play/Pause only**.
4. Confirm the cell is visible even with no media session while the stock panel
   is visible, and all OEM controls remain correct.
5. Start one media session and verify exactly one play/pause operation per tap.
6. Test paused/no-session/session-destroyed/multiple-session conditions.
7. Add Previous/Next only after Play/Pause passes.
8. Recheck measured cell sizes and all OEM functions.
9. Disable/re-enable and verify exactly-one group ownership.
10. Exercise a stock state that hides the panel; verify module controls disappear
    with it and return only after full preflight.
11. Qualify SystemUI restart, reboot, cold boot, ACC sleep/wake, reverse camera,
    reverse+DVR where available, calls, projection and long-duration use.

## STOP conditions

Remain stock if:

- identity/reflection/resources differ;
- mandatory visible OEM controls are missing;
- an unknown/duplicate direct child appears;
- known present stock controls are not directly/cleanly weighted;
- stock navigation root/host is hidden, detached or not shown;
- host width falls below the 48dp hard floor;
- projected vertical cell falls below 56dp;
- any OEM View/function/listener semantics regress;
- media selection/dispatch is ambiguous;
- one tap duplicates;
- an owned group survives disable/reinflation/stock-panel hiding incorrectly;
- SystemUI/input/boot stability regresses.

Do not broaden topology, force stock navigation visible, hide stock controls,
reduce the vertical production target, widen LSPosed scope or introduce a second
playback authority to bypass a STOP. Source/CI completion is not physical
acceptance.
