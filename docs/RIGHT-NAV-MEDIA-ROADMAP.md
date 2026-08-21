# Exact TS18 right-navigation media controls

## Product outcome

Optionally add Previous, Play/Pause and Next to the exact TS18 right-side
SystemUI navigation strip while preserving every OEM function and maintaining a
minimum 56dp cell. The feature is off by default, independently recoverable and
is a client of existing media authority only.

## Controlling evidence

The exact API29 SystemUI APK establishes:

- Topway-extended `NavigationBarView.onFinishInflate`;
- vertical weighted `com.android.systemui:id/navbar_left`;
- direct stock IDs for power, Home, Back, Recents, app slot, volume up and
  volume down;
- existing `TWSystemUI/TWUtil` stock-command ownership, which must be retained;
- `android.permission.MEDIA_CONTENT_CONTROL`; and
- existing Android `MediaSessionManager`/`MediaController` use.

This supersedes the v0.4 assumption that exact SystemUI was missing. Analogous
FYT/UIS implementations remain secondary evidence.

## Implemented exact-host design

The after-stock lifecycle hook accepts only the exact class/method. Runtime
preflight then requires exact APK identity, direct vertical host, all seven
known stock children, no unknown child, stock height=0/uniform positive weights,
no explicit host weight sum, an attached laid-out host and projected cells
>=56dp.

One owner-tagged vertical group is inserted before the two stock volume buttons.
Outer group weight is `stockUnitWeight * actionCount`; each inner action weight
is 1. This proportionally reflows the exact recognised weighted host without
editing an OEM `LayoutParams`. Removal restores stock distribution because the
only added weight disappears.

The module does not instantiate Topway `KeyButtonView2`, replace the nav strip,
copy stock listeners, hide/move an OEM function, alter framework nav width or
take over `TWSystemUI/TWUtil` commands.

## Touch-size policy

- Configurable diagnostic floor: 48dp.
- Production enable minimum: 56dp.
- Width and projected height must both meet the requested production target.
- The current measured host, visible stock weights and action count are inputs;
  1280x720/55px/153dpi history is expectation only.
- A missing/unknown child, mixed weight, zero dimension or insufficient fit is a
  normal STOP that leaves stock nav intact.

## Media authority

`NavMediaSessionRepository` runs on a dedicated HandlerThread:

1. observe active sessions with `MediaSessionManager`;
2. deterministically keep/select one usable existing `MediaController`;
3. observe its playback state/capabilities;
4. publish a small presentation snapshot on the main thread; and
5. map each click to at most one supported `TransportControls` command.

Playing current is sticky; otherwise a playing controller wins. When none is
playing, a usable current controller stays, then paused/other usable candidates
are selected deterministically.

The feature never creates a MediaSession, service, queue, notification or audio
focus, and never sends both TransportControls and a media-key fallback. Guessed
`TWSystemUI.write(...)` values are prohibited.

## Presentation and lifecycle

Buttons use module-owned vector assets, generated runtime IDs and a keyed owner
tag. Unsupported commands are disabled and visually de-emphasized. Play/Pause
changes icon/content description from selected playback state. A disabled click
dispatches nothing.

Attach/layout/reinflation reconciliation is exactly-once. Root detach,
disablement, topology change or nav breaker stops session observation and
removes only the owned group. Probe state and process failures are isolated from
the compact status-bar breaker.

Settings generation 2:

```text
ts18_statusbar_nav_policy_version=2
ts18_statusbar_nav_enabled=0
ts18_statusbar_nav_probe_enabled=0
ts18_statusbar_nav_actions=previous,play_pause,next
ts18_statusbar_nav_min_touch_dp=56
ts18_statusbar_nav_debug=0
```

## Physical progression

1. Stock baseline and recovery drill.
2. Nav probe only across attach/reinflation and operational states.
3. One inert/disabled owned group observation if the harness requires it.
4. Play/Pause only with no-session, playing, paused, multi-session and app-death
   tests.
5. Add Previous/Next and repeat size/exactly-once checks.
6. SystemUI restart, reboot, cold boot, ACC sleep/wake and long-duration test.

Detailed acceptance criteria are in `VALIDATION.md`.

## STOP conditions

Remain stock if identity/reflection/resources differ; topology is ambiguous;
any OEM View/function/listener is missing; an unknown direct child appears;
uniform weights or measured 56dp fit fails; media selection/dispatch is
ambiguous; one tap duplicates; the group survives disable/reinflation; or
SystemUI/input/boot stability regresses.

Source/CI completion is not physical acceptance. The implementation remains
unverified on the unit until the full staged matrix is recorded.
