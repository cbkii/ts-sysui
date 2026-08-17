# Roadmap

## v0.3 baseline — hardened compact status bar

The compact status-bar baseline remains deliberately bounded:

- framework status-bar height at 43 dp;
- collapsed shade touch strip hard-capped to 20% of screen width;
- mandatory >=64 px top-corner exclusion;
- right-navigation inset excluded;
- observation-only LSPosed first run;
- optional, separately armed 0.75 collapsed visual scaling;
- framework RRO as the sole status-bar height authority;
- no `system_server` hooks.

## v0.4 — right-navigation observation foundation

The right-side navigation bar is still **not mutated**. v0.4 adds the safe
engineering foundation required before media controls can be considered:

- recognise and weakly track the live `TYPE_NAVIGATION_BAR` root through the
  existing exact WindowManager hooks;
- independently arm a bounded, rate-limited hierarchy probe;
- record current public View hierarchy/bounds/lifecycle evidence;
- maintain a navbar-specific circuit breaker so nav failures cannot disable the
  compact status-bar runtime;
- parse optional Previous / Play-Pause / Next subset/order;
- calculate safe capacity/slots with a pure tested layout policy;
- default all navbar observation/mutation settings off;
- keep `ts18_statusbar_nav_enabled=0`; there is no clickable nav control in this
  milestone.

The complete product contract, evidence gates, phased implementation sequence,
validation matrix and STOP conditions are in
[`RIGHT-NAV-MEDIA-ROADMAP.md`](RIGHT-NAV-MEDIA-ROADMAP.md).

## Later candidate — functional right-navigation media controls

The intended optional group remains:

```text
Previous
Play / Pause
Next
```

Controls must target an **existing** Android MediaSession/MediaController and
must not create playback, queue, notification, audio-focus or media-database
authority.

Before the first inert marker or functional control, the exact current full
`com.android.systemui` APK and a fresh physical hierarchy/lifecycle capture must
establish:

1. the exact owner/host of the right navigation surface;
2. genuinely unused space across relevant vehicle/UI states;
3. existing stock controls and their touch semantics/bounds;
4. exactly-once root reinflation/injection behaviour;
5. SystemUI's ability to control the intended active MediaController on API 29.

If those cannot be established, STOP at observation-only rather than replacing
or overlaying the whole navigation bar.
