# Exact-device validation matrix

Repository/CI validation and physical qualification are different evidence
classes. Record timestamps, build fingerprint, installed artifact hashes,
settings, overlay state and bounded logs for every physical stage.

Run the read-only collector before and after each change:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-validate.sh'
```

Use `ts18-right-nav-evidence.sh` for the bounded nav hierarchy/lifecycle report.

## Universal acceptance conditions

- Exact SystemUI contract reports `SUPPORTED` with the expected APK SHA-256.
- No SystemUI crash loop, ANR, input lockout or repeated circuit-breaker opening.
- Status/shade, keyguard, HUN, bubbles, calls, reverse camera and projection keep
  their intended stock behaviour unless a tested feature explicitly changes it.
- Collapsed changed strip is no wider than 20% of full physical width, at least
  64px from both top corners and does not overlap the right nav.
- Every OEM nav function remains present and correct; every enabled media cell
  measures at least 56dp.
- Disabling a layer restores only that layer without partition changes.

At last-observed 1280px width, the maximum changed touch strip is 256px. With a
55px right nav and 64px corner gap, expected half-open x bounds are [960,1216).
Treat these numbers as a measurement check, not a hardcoded runtime assumption.

## Stage 0 — stock baseline

All project artifacts disabled. Capture contract, overlay/package/window/input,
density, nav hierarchy and logs. Exercise shade, app top-edge input, every OEM
nav control, media, keyguard, call, reverse camera and projection.

Acceptance: stable stock reference and a known recovery route.

## Stage 1 — geometry RRO only

Enable only `ts18_statusbar_geometry`, reboot and measure status height/insets.

Acceptance: about 43dp status geometry, right nav unchanged, no clipping/input
or lifecycle regression. Repeat SystemUI restart and full reboot.

## Stage 2 — visual RRO

Keep geometry enabled, add only `ts18_statusbar_visuals`, reboot and inspect
overlay/idmap state plus status icons/clock in all ordinary and special states.

Acceptance: only approved status visuals shrink; nav/shared layout does not
change. Rejection or missing target resources is a clean STOP with stock visuals.

## Stage 3 — LSPosed hook load, mutations off

Install/scope the APK only to main `com.android.systemui`. Compact generation 4,
nav generation 2 and brightness generation 1 remain disabled.

Acceptance: exact identity resolves asynchronously, hooks load once, no view,
touch or brightness mutation occurs, restart/reboot stable.

## Stage 4 — exact collapsed touch

Arm exact input, preferably below 20% first. Test shade gestures inside the
strip and app gestures immediately outside it. Measure input/window reports.
Repeat for expanded shade, keyguard/bouncer, HUN, bubbles, rotation if supported
and immersive transitions.

Acceptance: ordinary collapsed routing changes only inside bounds; special
states retain stock behaviour; `input-off` restores stock routing.

## Stage 5 — nav observation, mutation off

Arm only `nav-observe`, capture hierarchy through attach/detach/reinflation and
normal/immersive/keyguard/reverse-camera/call/projection transitions.

Acceptance: exact vertical `navbar_left`, seven known direct functions, uniform
weighted stock cells and no unknown/duplicate host. Probe disable removes its
listener and changes no view.

## Stage 6 — Play/Pause only

Set `nav-actions play_pause`, 56dp minimum and `nav-enable`. Restart SystemUI.
Measure all stock and owned cells and record their order, bounds, IDs/listeners
where observable and press feedback.

Test no session, one playing session, one paused session, two simultaneous
sessions, session destruction and unsupported action.

Acceptance: stock functions unchanged; cell >=56dp; no-session/unsupported is
disabled; icon follows selected state; one tap yields exactly one play or pause;
disable/reinflation removes only the owned group.

## Stage 7 — Previous / Play-Pause / Next

Enable all three in the configured order. Repeat measurements and multi-session
tests. At the historical 720px host, expected projected cells are about 80px for
six visible OEM cells or 72px when the app slot is visible.

Acceptance: every visible stock and media cell remains >=56dp; each tap maps to
one supported transport command; no media-key/vendor fallback or duplicate
dispatch is observed.

## Stage 8 — operational state matrix

With intended non-brightness features enabled, exercise:

- ordinary launcher/app use and rapid shade gestures;
- keyguard, screen off/on and power button;
- pinned/departing HUN and bubbles if present;
- calls and audio route changes;
- reverse camera and parking/vehicle UI;
- CarPlay/Android Auto/projection and immersive apps;
- volume up/down, mute/power, Home, Back, Recents and app slot;
- media app switching, paused background session, no session and app death.

Acceptance: no OEM-function regression, overlap, stale controller, duplicate
command or SystemUI instability.

## Brightness BR0 — observation/config bridge only

Keep `ts18_brightness_enabled=0`. Confirm the exact SystemUI compatibility gate
passes, brightness lifecycle/callback hooks load, and the configuration Activity
can receive a valid private acknowledgement without enabling mutation. Capture
current `258` mode and `516` Day/Night callback state where available.

Acceptance: no Topway brightness write attributable to the controller; config
request/acknowledgement is bounded; normal stock brightness surfaces still work.

## Brightness BR1 — fixed Day at a safe visible level

Select a conservative non-zero managed Day level and `mode=day`, then enable the
brightness controller. Change one variable at a time and capture the follow-up
Topway callback/query state.

Acceptance: the requested Day slot/mode converges without repeated writes;
disabling brightness returns ownership to stock behaviour immediately.

## Brightness BR2 — fixed Night at a safe visible level

Repeat BR1 for `mode=night` using a clearly visible non-zero Night level.

Acceptance: the Night slot/mode converges, stock slider/CarSetting remains
usable after disable, and no brightness-only circuit breaker opens.

## Brightness BR3 — Set auto transition in both directions

Choose near-future Day and Night times so each transition can be observed within
a bounded test window. Test Day -> Night and Night -> Day separately. Record the
clock, settings and `258`/`516` callback state before and after each transition.

Acceptance: exactly one semantic mode transition is required at each boundary;
no hot loop or continuous rewrite occurs. Screen-off/on across a boundary must
also reconcile correctly.

## Brightness BR4 — coexistence and vehicle-state checks

With safe non-zero levels, exercise:

- stock SystemUI brightness slider and CarSetting brightness UI;
- `ts18_brightness_enabled=0` and the root helper disable path;
- headlights/ILL on and off while fixed and scheduled modes are tested;
- reverse-camera entry/exit; and
- ordinary launcher/app operation.

Acceptance: brightness uses only the recovered Topway semantic path, does not
fight stock writers while disabled, and does not disturb camera/vehicle UI.

## Brightness BR5 — lifecycle qualification

With an already proven safe brightness policy, test in order:

1. SystemUI restart;
2. launcher restart;
3. warm Android reboot;
4. cold boot after full power removal where safe; and
5. repeated ACC sleep/wake cycles.

Acceptance: brightness remains fail-open until exact identity and transport
readiness are re-established; the intended saved policy recovers without a
SystemUI loop or repeated writes; kill-switch recovery remains available.

**Managed level 0 is not part of BR0–BR5.** It remains blocked until a separate,
explicitly planned timed no-backlight recovery test proves that this exact panel
can be restored safely. Do not weaken the 1..10 production guard to complete the
normal brightness matrix.

## Stage 9 — combined lifecycle qualification

After the applicable brightness BR stages and non-brightness stages have each
passed independently, exercise the intended combined feature set. Perform in
order, collecting a fresh report after each:

1. proven SystemUI restart;
2. normal Android reboot;
3. cold boot after full power removal where safe;
4. multiple ACC sleep/wake cycles; and
5. representative long-duration drive/standby interval.

Acceptance: overlays persist, identity resolves, exactly one owned nav group
exists, brightness remains bounded and independently recoverable, settings remain
intended, stock recovery works and no repeated exception/ANR appears.

## STOP evidence

On a failure, record the smallest exact reproduction, settings, contract output,
overlay list, validator/nav report and bounded logs. Disable only the affected
layer. Do not broaden hashes/topology, reduce production nav target below 56dp,
replace SystemUI, add a system-server hook, infer a vendor command as a fix, or
bypass the brightness level-0 safety gate.

Only after every applicable stage passes may the result be labelled physically
qualified. Until then use `source-validated`, `CI-validated` or `unverified on
physical TS18` precisely.
