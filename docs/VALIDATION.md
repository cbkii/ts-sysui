# Exact-device validation matrix

Repository/CI validation and physical qualification are different evidence
classes. Current exact-device evidence is now:

- compact top-right collapsed-shade routing is physically working;
- the previously installed right-nav media feature produced no custom controls;
- the previously installed brightness options produced no useful physical
  brightness result; and
- exact supplied SystemUI/CarSetting analysis identified concrete source
  corrections for both failed paths.

The corrected nav/brightness implementation and the new XTService observation
layer are not physically successful until the applicable stages below are
recorded on the exact `s9863a1h10` Android 10/API29 unit.

## Evidence to record

For each consequential stage record:

- timestamp and boot/lifecycle boundary;
- installed project version/build kind;
- combined Magisk module state;
- exact SystemUI identity/hash result;
- exact CarSetting brightness-contract/hash result;
- exact XTService identity/hash/bind/callback state when applicable;
- TS18 System UI, Diagnostic Console or Topway Qualification export;
- feature settings;
- relevant physical observation; and
- PASS / FAIL / STOP outcome.

The dashboard/Diagnostic Console is the preferred first diagnostic source. Root
collectors remain supplementary; do not replace them with repeated Binder-heavy
shell commands known to be unreliable on this firmware.

## Universal acceptance conditions

- exact SystemUI = `SUPPORTED` with SHA-256
  `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`;
- managed brightness additionally reports exact CarSetting contract SHA-256
  `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71`;
- any private XTService Binder use additionally reports the current installed
  `com.tw.service.xt` SHA-256
  `341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267`;
- LSPosed scope remains only main `com.android.systemui`;
- no SystemUI crash loop, ANR, input lockout or repeated breaker opening;
- no OEM Home/Back/Recents/Volume function regresses;
- no project code replaces SystemUI or writes Android partitions;
- collapsed touch never exceeds 20% physical width or enters the 64px corner
  exclusions;
- every injected media control uses the existing sidebar width >=48dp and
  projected vertical size >=56dp;
- reverse/sleep observation never mutates OEM visibility or vehicle state;
- one accepted normal media tap results in at most one Android transport
  operation, and one explicit qualification tap results in at most one XTService
  Binder operation;
- no normal right-nav action sends both Android and XTService commands;
- brightness success is never inferred from policy persistence or 516 state;
- physical brightness success requires `SCREEN_BRIGHTNESS` readback convergence
  and a visible panel change; and
- disabling one behavioural layer restores stock ownership of that layer.

At the last-observed 1280px width, the maximum collapsed strip remains 256px;
with the historic 55px right nav and 64px corner gap, expected half-open bounds
are `[960,1216)`. These remain measurement checks, not hard-coded runtime
geometry.

# Installation / RRO stages

## Stage I0 — stock/recovery reference

Before installing the correction build, record current stock/project behaviour,
confirm Home/Back/Recents/Volume+/Volume−, confirm stock CarSetting brightness,
and confirm Magisk/LSPosed recovery routes.

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
- brightness compatibility reports exact CarSetting contract rather than only
  the SystemUI hash;
- physical raw `screen_brightness` is observable;
- no behavioural mutation occurs merely from installation; and
- no breaker is open.

If the bridge cannot respond, STOP. Do not widen LSPosed scope.

## Stage L1 — compact collapsed touch regression

The <=20% path is already physically proven, but recheck it after this build to
ensure the nav/brightness/XTService work did not regress it. Test ~10%, inside
and outside the strip, expanded shade, keyguard/bouncer, heads-up/bubbles if
present, and immersive/fullscreen transitions.

Acceptance: the physically proven compact behaviour remains unchanged.

# Exact XTService observation stages

## Stage X0 — installed identity, bind and initial read-only state

Use the diagnostic build and open **TS18 Topway Qualification** without pressing
any vendor media button. Record:

```text
XTService expected/current SHA-256
version
identity state/detail
bind state
callback registered
last bind attempt
reverse known/status/timestamp/age
sleep known/status/timestamp/age
XTService breaker/failure count
```

Acceptance:

- current installed XTService matches the exact supplied SHA-256;
- the explicit `CommandService` bind succeeds from the SystemUI process;
- callback registration succeeds;
- initial reverse/sleep queries produce plausible fresh state; and
- no vendor media command is issued merely by observation/launch.

Hash mismatch, unresolved component/action, repeated bind/register/query failure
or incompatible callback values is a STOP for XTService use. Do not broaden the
identity gate or LSPosed scope.

## Stage X1 — reverse veto and fresh return preflight

With normal right-nav controls otherwise passing N0/N1, enter reverse while
parked and safe, then exit reverse.

Acceptance:

- reverse-active callback becomes fresh/known;
- only the module-owned media group is suspended/removed;
- stock reverse camera and OEM navbar visibility behaviour is unchanged;
- no media command is emitted by the transition;
- reverse exit requires a fresh inactive callback; and
- module-owned controls return only after the normal exact stock visibility,
  topology and measurement preflight succeeds again.

If the service disconnects after a known active reverse state, confirm stale
active state remains a module-only veto until a fresh inactive observation.

## Stage X2 — sleep/wake and ACC reconnect

Exercise screen-off/sleep/wake and repeated ACC sleep/wake boundaries where safe.

Acceptance:

- sleep-active removes/suspends module-owned nav media only;
- wake requires fresh inactive state and fresh nav preflight;
- service death/binding death does not leak duplicate connections/callbacks;
- bounded rebind restores exactly one callback registration;
- a known-active state is not silently converted to inactive on disconnect; and
- XTService breaker failure does not disable compact touch or brightness.

Repeat across a SystemUI restart and warm reboot before cold-boot/ACC acceptance.

## Stage X3 — explicit vendor-media qualification only

The diagnostic **TS18 Topway Qualification** screen is the only supported path
for these calls in this build. Test separately against:

1. no media/source idle;
2. ordinary Android MediaSession playback;
3. stock local music;
4. Bluetooth media;
5. radio; and
6. any other stock source observed on the unit.

For every source, issue one explicit test at a time:

```text
Vendor Previous
Vendor Play
Vendor Pause
Vendor Next
```

Record requested action, Binder result, timestamp and physical effect separately.

Acceptance for qualification data collection:

- one button press -> at most one XTService Binder method;
- no Android `MediaController.TransportControls` operation is emitted by that
  diagnostic press;
- Binder return is never labelled playback success without the physical result;
- normal right-nav buttons remain Android-MediaController-only; and
- source-dependent, duplicated or unsafe behaviour is recorded as a STOP for any
  later automatic fallback proposal.

This stage does **not** authorise automatic vendor-media fallback.

## Stage X4 — stock navigation configuration observation

Record the read-only values of:

```text
persist.navibar.position
Settings.System.navigationbar_config
Settings.System.show_navigationbar
```

If a normal stock settings action safely changes one of these values, observe the
transition without using the module to write it.

Acceptance:

- diagnostics report the current values and transition time/reason;
- a changed value removes current module-owned nav media before reuse;
- the live navbar then passes a fresh exact topology/measurement preflight; and
- no module code writes or forces the stock configuration.

Do not infer the active two-button/three-button/gestural overlay solely from the
supplied overlay APKs.

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
vehicle veto/reason
nav breaker state
```

The expected exact resource names from the supplied SystemUI are:

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

Acceptance: the runtime topology is explained and uses those exact resource
names. Unknown/duplicate direct child, missing mandatory control or unsafe
geometry is a STOP. Do not reintroduce generic `home`, `back`, `recent_apps` or
`app` matching.

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

## Stage BR0 — exact backend observation, mutation off

Record:

```text
brightness hooks/compatibility
CarSetting contract SHA-256
Topway transport ready
Topway mode known/current
516 observation known/effectiveNight/daySlot/nightSlot
physical backend
observed raw screen_brightness
last physical read timestamp
last 258/516 callbacks
last stock Topway write
brightness setting-change previous/current/timestamp
brightness correlation classification/delta/note
breaker state
```

Operate the stock CarSetting brightness slider through several safe values and
refresh diagnostics.

Acceptance:

- raw `screen_brightness` changes in the same direction as the stock slider;
- module reports 516 values as observation-only;
- diagnostic correlation stays explicitly temporal/unknown unless a module-owned
  exact requested write establishes the stronger case; and
- no module physical write is attributed while disabled.

If stock CarSetting visibly changes the panel but raw `screen_brightness` does
not track at all, STOP: the supplied static branch is not the current runtime
path and must be re-investigated before mutation.

## Stage BR1 — fixed Day physical output

Choose a safe managed Day level clearly different from the current raw value and
apply **Test Day**.

Required evidence:

```text
policy saved
-> exact compatibility ready
-> Topway transport/mode state ready
-> 258 mode action if needed
-> 258,1,<DAY> stage sent
-> 258,128 stage sent
-> 258 callback/state confirms Day
-> physical logical level mapped to raw 30..255
-> SCREEN_BRIGHTNESS write
-> SCREEN_BRIGHTNESS readback matches requested raw
-> ACTIVE/SETTLED
```

Acceptance: readback converges **and** panel brightness visibly changes to the
intended safe Day level. A policy acknowledgement or 516 callback alone is not
success.

## Stage BR2 — fixed Night physical output

Repeat BR1 with a deliberately distinct safe Night level.

Acceptance: Topway Night mode transaction/state confirms, raw physical readback
converges, and the visible panel result is distinct from Day.

Relevant STOP reasons include:

```text
NO_258_CALLBACK
SCREEN_BRIGHTNESS_READBACK_MISMATCH
BLOCKED:TRANSPORT_NOT_READY
BLOCKED:IDENTITY_OR_CLASS_CONTRACT
ERROR:BREAKER_OPEN
```

## Stage BR3 — stock Auto with managed levels

Select **Auto (stock)** and deliberately distinct safe Day/Night managed levels.
Exercise a safe stock condition that changes effective Day/Night if available
(e.g. headlights/ILL while parked).

Acceptance:

- Topway remains in mode 0;
- no local-clock Day/Night mode forcing occurs;
- valid 516 observation establishes `effectiveNight` before the module chooses a
  managed physical level;
- unknown effective state produces `BLOCKED:AUTO_EFFECTIVE_STATE_UNKNOWN` and no
  guessed physical write; and
- physical output confirms by raw readback.

## Stage BR4 — local Set auto

Choose near-future transitions and test Day->Night and Night->Day separately,
including screen-off/on across a boundary.

Acceptance: one required explicit Day/Night mode transaction per boundary,
corresponding managed physical level/readback, no continuous rewriting and
correct recovery after sleep/missed boundary.

## Stage BR5 — coexistence and restore

Test:

- module brightness disabled versus enabled;
- stock CarSetting/SystemUI brightness control;
- headlights/ILL on/off;
- reverse-camera entry/exit;
- ordinary launcher/app use.

Acceptance: disabled module does not fight stock writers. Re-enabling reconciles
only after exact gates/state are ready. The module never falls back to 516
physical writes, sysfs, panel calibration or a second actuator.

# Combined lifecycle qualification

Only after compact, XTService observation, nav and brightness pass independently,
test the intended combined set through:

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
- exactly one XTService connection/callback registration after recovery;
- no duplicate media command;
- reverse/sleep remain module-only vetoes and never become vehicle-state
  actuators;
- brightness re-establishes both exact binary gates, Topway transport/state and
  physical readback before mutation;
- intended settings survive only where designed;
- independent kill switches/recovery remain usable; and
- no repeated SystemUI exception/ANR or vehicle-UI regression.

# STOP evidence

On failure save dashboard/Diagnostic Console/Topway Qualification diagnostics
plus the smallest physical reproduction and disable only the affected layer.

Do not respond to a STOP by broadening hashes/topology, widening LSPosed scope,
reducing nav safety targets, hiding/replacing OEM controls, introducing automatic
vendor-media fallback or another media authority, replacing/resigning SystemUI,
writing Android partitions, using brightness level 0, restoring ordinary 516
physical writes, or adding a sysfs fallback.

If `SCREEN_BRIGHTNESS` write/readback converges but physical brightness still
does not change, collect a narrow stock-CarSetting-vs-module runtime trace before
changing actuator authority again.

Only after the applicable stages pass may the build be called physically
qualified. Until then use `source-validated`, `CI-validated` and `physical TS18
validation outstanding` precisely.
