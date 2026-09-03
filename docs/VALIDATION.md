# Exact-device validation matrix

Repository/CI validation and physical qualification are different evidence
classes. Current exact-device evidence is now:

- compact top-right collapsed-shade routing is physically working;
- the previously installed right-nav media feature produced no custom controls;
- the previously installed brightness options produced no useful physical
  brightness result;
- exact SystemUI analysis corrected the live navbar resource IDs;
- exact CarSetting analysis corrected the two-stage 258 mode transaction; and
- retained exact-device runtime plus exact current SystemUI evidence establishes
  Topway 516 as the active Day/Night 0..10 brightness authority, while Android
  `screen_brightness` remained unchanged during that trace.

The corrected nav/brightness implementation is not physically successful until
the applicable stages below are recorded on the exact `s9863a1h10` Android
10/API29 unit.

## Evidence to record

For each consequential stage record:

- timestamp and boot/lifecycle boundary;
- installed project version/build kind;
- combined Magisk module state;
- exact SystemUI identity/hash result;
- exact CarSetting brightness-contract/hash result;
- TS18 System UI or Diagnostic Console export;
- feature settings;
- relevant physical observation; and
- PASS / FAIL / STOP outcome.

The dashboard/Diagnostic Console is the preferred first diagnostic source. Root
collectors remain supplementary; do not replace them with repeated Binder-heavy
shell commands known to be unreliable on this firmware.

## Universal acceptance conditions

- exact SystemUI = `SUPPORTED` with SHA-256
  `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`;
- this qualification build also recognises supplied CarSetting SHA-256
  `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71`;
- LSPosed scope remains only main `com.android.systemui`;
- no SystemUI crash loop, ANR, input lockout or repeated breaker opening;
- no OEM Home/Back/Recents/Volume function regresses;
- no project code replaces SystemUI or writes Android partitions;
- collapsed touch never exceeds 20% physical width or enters the 64px corner
  exclusions;
- every injected media control uses the existing sidebar width >=48dp and
  projected vertical size >=56dp;
- one accepted media tap results in at most one transport operation;
- brightness success is never inferred from policy persistence alone;
- managed Day/Night slot success requires matching 516 callback state **and** a
  visible panel change during physical qualification;
- mode success requires matching 258 state after the two-stage stock transaction;
- Android `screen_brightness` is diagnostic-only and is not a success predicate;
- disabling one behavioural layer restores stock ownership of that layer.

At the last-observed 1280px width, the maximum collapsed strip remains 256px;
with the historic 55px right nav and 64px corner gap, expected half-open bounds
are `[960,1216)`. These remain measurement checks, not hard-coded runtime
geometry.

# Installation / RRO stages

## Stage I0 — stock/recovery reference

Before installing the correction build, record current stock/project behaviour,
confirm Home/Back/Recents/Volume+/Volume−, confirm stock CarSetting/SystemUI
brightness, and confirm Magisk/LSPosed recovery routes.

Acceptance: known stable reference and rollback route.

## Stage I1 — combined `ts18_sysui` Magisk module

Install one current `TS18-SystemUI-Magisk-*` ZIP and reboot.

Acceptance:

- both RRO payload paths are mounted;
- intended status geometry/visuals render;
- right-nav width/functions remain stock before media injection; and
- no boot/SystemUI/launcher/reverse-camera regression.

Payload presence is not sufficient proof of idmap/resource use.

# LSPosed / dashboard stages

## Stage L0 — hooks loaded, behaviour off

Install/update the LSPosed APK and scope only `com.android.systemui`. Reboot with
compact/nav/brightness mutations off. Open **TS18 System UI** and, for a
diagnostic build, **TS18 Diagnostic Console**.

Acceptance:

- bridge responds;
- exact SystemUI identity reports `SUPPORTED`;
- navbar hook status is visible;
- brightness compatibility is explained;
- Topway 258/516 state and Android `screen_brightness` diagnostic mirror are
  observable;
- no behavioural mutation occurs merely from installation; and
- no breaker is open.

If the bridge cannot respond, STOP. Do not widen LSPosed scope.

## Stage L1 — compact collapsed touch regression

The <=20% path is already physically proven, but recheck it after this build to
ensure the nav/brightness correction did not regress it. Test ~10%, inside and
outside the strip, expanded shade, keyguard/bouncer, heads-up/bubbles if present,
and immersive/fullscreen transitions.

Acceptance: the physically proven compact behaviour remains unchanged.

# Right-nav correction stages

## Stage N0 — exact topology observation

Enable nav diagnostics/probe as needed and record:

```text
nav hook installed
root seen/attached
navbar_left host seen
preflight reason
host width/height/density
live direct-child resource names
recognised stock summary
projected cell size
horizontal floor/preferred result
nav breaker state
```

Expected exact resource names:

```text
required:
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce

optional:
navbar_guanping
navbar_app
```

Acceptance: runtime topology is explained with those names. Unknown/duplicate
direct child, missing mandatory control or unsafe geometry is a STOP. Do not
reintroduce generic `home`, `back`, `recent_apps` or `app` matching.

## Stage N1 — Play/Pause only, no session

Configure only Play/Pause and enable custom sidebar controls.

Acceptance:

- one module-owned Play/Pause cell appears without a SystemUI restart;
- it remains visible but disabled with no usable MediaController;
- every OEM control stays correct;
- projected vertical size >=56dp;
- existing strip width >=48dp;
- no widening, overlap or scrolling; and
- diagnostics report active/injected state.

Complete absence remains a FAIL/STOP with the exact preflight/direct-child
reason, not a media-session failure.

## Stage N2 — Play/Pause with sessions

Test playing, paused, no session, session destruction, two simultaneous sessions
and media-app switch.

Acceptance: deterministic sticky selection, callback-driven enabled state and
exactly one play or pause command per accepted tap.

## Stage N3 — Previous / Play-Pause / Next

Enable all three in the desired order.

Acceptance:

- all module/OEM cells retain safe measured size;
- unsupported actions remain visible but disabled;
- one supported tap maps to one `TransportControls` call;
- reinflation/disable/re-enable never duplicates the group; and
- removal restores the stock hierarchy without reconstructing OEM Views.

# Brightness correction stages

## Stage BR0 — exact authority observation, mutation off

Record:

```text
brightness hooks/compatibility
Topway transport ready
Topway 258 mode known/current
516 levelsKnown/effectiveNight/daySlot/nightSlot
last 258/516 callbacks
last observed stock Topway write
Android screen_brightness diagnostic mirror
breaker state
```

Operate the stock SystemUI/CarSetting brightness UI through several safe values
and refresh diagnostics.

Acceptance:

- 516 Day/Night values or effective state respond consistently with the stock
  Topway brightness interaction;
- Android `screen_brightness` is clearly labelled diagnostic-only;
- no module write is attributed while disabled.

If the stock UI visibly changes panel brightness without any corresponding
258/516 evidence, STOP and capture a narrow marker-assisted trace before
mutation. Do not automatically switch actuator authority.

## Stage BR1 — managed Day slot + Day mode

Choose a safe non-zero Day level clearly different from the currently observed
516 Day slot and apply **Test Day**.

Required evidence:

```text
policy saved
-> exact compatibility ready
-> Topway transport/state ready
-> write(516,0,<Day>) if slot differs
-> 516 callback confirms Day slot
-> write(258,1,1) if mode differs
-> write(258,128)
-> 258 callback/state confirms Day
-> ACTIVE/SETTLED
```

Acceptance: semantic state converges and panel brightness visibly changes to the
intended safe Day level. Policy persistence or an unrelated Android setting
change is not success.

## Stage BR2 — managed Night slot + Night mode

Repeat BR1 with a deliberately distinct safe Night level.

Acceptance: 516 Night slot and 258 Night mode converge, both mode transaction
stages are observed, and the visible panel result is distinct from Day.

Relevant STOP reasons include:

```text
NO_258_CALLBACK
NO_516_CALLBACK
BLOCKED:TRANSPORT_NOT_READY
BLOCKED:IDENTITY_OR_CLASS_CONTRACT
ERROR:BREAKER_OPEN
```

## Stage BR3 — stock Auto

Select **Auto (stock)** with deliberately distinct safe Day/Night managed slots.
Exercise a safe stock condition that changes effective Day/Night if available
(e.g. headlights/ILL while parked).

Acceptance:

- Topway remains in mode 0;
- configured 516 Day/Night slots remain confirmed;
- effectiveNight changes only according to stock Topway behaviour;
- no local-clock Day/Night forcing occurs in stock Auto;
- visible output follows the stock effective condition.

## Stage BR4 — local Set auto

Choose near-future transitions and test Day->Night and Night->Day separately,
including screen-off/on across a boundary.

Acceptance: configured 516 slots remain settled, one required two-stage 258 mode
transaction occurs per boundary, callbacks converge and there is no continuous
rewriting.

## Stage BR5 — coexistence and restore

Test:

- module brightness disabled versus enabled;
- stock CarSetting/SystemUI brightness control;
- headlights/ILL on/off;
- reverse-camera entry/exit;
- ordinary launcher/app use.

Acceptance: disabled module does not fight stock writers. Re-enabling reconciles
only after exact gates/state are ready. The module never falls back to Android
`screen_brightness`, sysfs, panel calibration or a second actuator.

# Combined lifecycle qualification

Only after compact, nav and brightness pass independently, test the intended
combined set through:

1. normal launcher/app use;
2. SystemUI restart;
3. launcher restart;
4. warm Android reboot;
5. cold boot/full power removal where safe;
6. repeated ACC sleep/wake;
7. reverse camera and reverse+DVR if available;
8. phone call/audio routing;
9. projection/immersive mode; and
10. representative long-duration drive/standby.

Acceptance:

- exactly one module-owned nav group;
- no duplicate media command;
- brightness re-establishes exact gates, Topway transport and 258/516 state before
  mutation;
- intended settings survive only where designed;
- independent kill switches/recovery remain usable; and
- no repeated SystemUI exception/ANR or vehicle-UI regression.

# STOP evidence

On failure save dashboard/Diagnostic Console diagnostics plus the smallest
physical reproduction and disable only the affected layer.

Do not respond to a STOP by broadening hashes/topology, widening LSPosed scope,
reducing nav safety targets, hiding/replacing OEM controls, introducing another
media authority, replacing/resigning SystemUI, writing Android partitions,
using brightness level 0, switching to Android `screen_brightness`, or adding a
sysfs fallback.

If module-generated 258/516 state is callback-confirmed but physical brightness
still does not change, collect a narrow stock-vs-module marker trace before
changing actuator authority.

Only after applicable stages pass may the build be called physically qualified.
Until then use `source-validated`, `CI-validated` and `physical TS18 validation
outstanding` precisely.
