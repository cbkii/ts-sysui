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

Install/scope the APK only to main `com.android.systemui`. Compact generation 4
and nav generation 2 remain disabled.

Acceptance: exact identity resolves asynchronously, hooks load once, no view or
touch mutation occurs, restart/reboot stable.

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

With intended features enabled, exercise:

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

## Stage 9 — lifecycle qualification

Perform in order, collecting a fresh report after each:

1. proven SystemUI restart;
2. normal Android reboot;
3. cold boot after full power removal where safe;
4. multiple ACC sleep/wake cycles; and
5. representative long-duration drive/standby interval.

Acceptance: overlays persist, identity resolves, exactly one owned group exists,
settings remain intended, stock recovery works and no repeated exception/ANR
appears.

## STOP evidence

On a failure, record the smallest exact reproduction, settings, contract output,
overlay list, validator/nav report and bounded logs. Disable only the affected
layer. Do not broaden hashes/topology, reduce production target below 56dp,
replace SystemUI, add a system-server hook or infer a vendor command as a fix.

Only after every applicable stage passes may the result be labelled physically
qualified. Until then use `source-validated`, `CI-validated` or `unverified on
physical TS18` precisely.
