# Exact-device validation matrix

Repository/CI validation and physical qualification are different evidence
classes. The current exact-device findings from 0.5.1 are part of the test
baseline: no right-nav media buttons were visible and Day/Night selection caused
no observable brightness change.

The remediation is not physically successful until the stages below are recorded
on the exact `s9863a1h10` Android 10/API29 unit.

## Evidence to record

For each consequential stage record:

- timestamp and boot/lifecycle boundary;
- build fingerprint and installed project version;
- combined Magisk module state;
- exact SystemUI identity/hash result;
- TS18 System UI dashboard diagnostic export;
- feature settings;
- relevant physical observation;
- PASS / FAIL / STOP outcome.

The dashboard is the preferred first diagnostic source. Root collectors remain
supplementary and should not be replaced by repeated hanging Binder-heavy shell
commands on this firmware.

## Universal acceptance conditions

- exact SystemUI = `SUPPORTED` with expected SHA-256;
- LSPosed scope remains only main `com.android.systemui`;
- no SystemUI crash loop, ANR, input lockout or repeated breaker opening;
- no OEM Home/Back/Recents/Volume function regresses;
- no project code replaces SystemUI or writes Android partitions;
- collapsed touch never exceeds 20% physical width or enters the 64px corner
  exclusions;
- every injected media control uses the existing sidebar width >=48dp and
  projected vertical size >=56dp;
- one accepted media tap results in at most one transport operation;
- brightness hardware success is never inferred from policy persistence alone;
- disabling one behavioural layer restores stock ownership of that layer.

At the last-observed 1280px width, the maximum collapsed strip remains 256px;
with the historic 55px right nav and 64px corner gap, expected half-open bounds
are [960,1216). These are measurement checks, not hardcoded runtime geometry.

# Installation / RRO stages

## Stage I0 — stock/recovery reference

Before installing the remediation build:

- record current stock/project behaviour;
- confirm Home, Back, Recents, Volume+ and Volume−;
- confirm stock brightness surfaces still work;
- confirm Magisk and LSPosed recovery routes.

Acceptance: known stable reference and rollback route.

## Stage I1 — migrate legacy split modules

If `ts18_statusbar_geometry` or `ts18_statusbar_visuals` remains installed, use
`ts18-migrate-magisk-modules.sh`, reboot and confirm both legacy IDs are gone.

Acceptance: no direct module-directory deletion; no duplicate project overlay
owners remain.

## Stage I2 — combined `ts18_sysui` Magisk module

Install one `TS18-SystemUI-Magisk-*` ZIP and reboot.

Acceptance:

- geometry and visual overlay payload paths are mounted;
- top status geometry moves toward the intended ~43dp result;
- only approved status icon/clock visuals change;
- right-nav width/functions remain stock;
- no boot/SystemUI/launcher/reverse-camera regression.

Payload presence is not sufficient proof of idmap/resource use; inspect the
rendered result.

# LSPosed / dashboard stages

## Stage L0 — hooks loaded, behaviour off

Install/update the LSPosed APK and scope only `com.android.systemui`. Reboot with
compact/nav/brightness mutations off. Open **TS18 System UI**.

Acceptance:

- bridge responds;
- exact identity reports `SUPPORTED`;
- navbar hook status is visible;
- brightness hook count/state is visible;
- no behavioural mutation occurs merely from installation;
- no breaker is open.

If the bridge cannot respond, STOP. Do not widen LSPosed scope.

## Stage L1 — exact collapsed touch

Enable compact touch at ~10% first, retain 64px corner gap and test:

- shade initiation inside the configured strip;
- top-edge app interaction immediately outside it;
- expanded shade;
- keyguard/bouncer;
- heads-up/bubbles if present;
- immersive/fullscreen transitions.

Acceptance: only ordinary collapsed routing changes; special/ambiguous states
remain stock. Then test up to, but not beyond, the required 20% maximum.

# Right-nav remediation stages

## Stage N0 — live preflight, mutation off

Refresh dashboard status with nav disabled. If needed enable diagnostic/probe
mode using the engineering helper and collect the exact host summary.

Record:

```text
nav hook installed
root seen/attached
navbar_left host seen
preflight reason
host width/height/density
mandatory/optional stock child summary
projected cell size
horizontal floor/preferred result
nav breaker state
```

Acceptance: the runtime topology is explained rather than silently ignored.
Unknown direct child, missing mandatory stock control or unsafe geometry is a
STOP.

## Stage N1 — Play/Pause only, no session

Configure only Play/Pause and enable custom sidebar controls from the dashboard.
No SystemUI restart should be required merely for the config change to be
consumed.

Acceptance:

- one module-owned Play/Pause cell becomes visible;
- it remains visible but disabled with no usable MediaController;
- every OEM control stays visible/correct;
- vertical projected size >=56dp;
- existing full strip width >=48dp;
- no nav strip widening, overlap or scrolling;
- dashboard preflight changes to active/injected state.

Complete absence of the cell is a FAIL/STOP with the dashboard preflight reason,
not a media-session failure.

## Stage N2 — Play/Pause with sessions

Test:

- one playing session;
- one paused session;
- no session;
- session destruction;
- two simultaneous sessions;
- media app switch.

Record controller count, selected package, playback state/action bits and button
state.

Acceptance: deterministic sticky selection, callback-driven button state and
exactly one play or pause command per tap.

## Stage N3 — Previous / Play-Pause / Next

Enable all three in the desired order.

Acceptance:

- all media and visible OEM cells still satisfy production vertical size;
- unsupported Previous/Next stay visible but disabled;
- one supported tap maps to one `TransportControls` call;
- disabling/re-enabling or reinflation never duplicates the group;
- removing the group restores the stock hierarchy without reconstructing stock
  Views.

# Brightness remediation stages

## Stage BR0 — observe exact runtime state

Keep brightness mutation off. Refresh dashboard status and record:

```text
brightness hooks/compatibility
transport ready
mode known
levels known
Topway mode
effectiveNight
detected Day level
detected Night level
last 258 callback
last 516 callback
last stock 258/516 write
breaker state
```

Operate the stock brightness slider/CarSetting once where safe and refresh.

Acceptance: the module can distinguish whether the private hooks/transport and
callbacks are actually alive. No module write is attributed while disabled.

## Stage BR1 — interpret Day/Night slots

If detected Day and Night values are equal, record that fact. A mode-only change
is not expected to produce a visible difference in that state.

Choose deliberately distinct safe non-zero managed values. Do not use level 0.

Acceptance: test values are explicit and recoverable; no hidden assumption that
stock slots differ.

## Stage BR2 — fixed Day

Use **Test Day** or apply an enabled fixed-Day policy with a safe managed Day
level.

Required observable sequence:

```text
policy saved
-> transport/state ready
-> ACTION_PENDING
-> 258/516 callback confirmation as applicable
-> ACTIVE/SETTLED
```

Acceptance:

- required slot/mode converges semantically;
- dashboard reports callback-confirmed state, not persistence-only success;
- physical brightness visibly matches the intended Day result;
- no hot loop or immediate breaker opening;
- Restore/disable returns ownership safely.

## Stage BR3 — fixed Night

Repeat BR2 for Night with a deliberately different safe level.

Acceptance: confirmed Topway Night state plus a physically distinct visible
brightness from the Day test.

`NO_258_CALLBACK`, `NO_516_CALLBACK`, transport-not-ready, unconfirmed write or
breaker-open is a real STOP reason and must be retained in the diagnostic report.

## Stage BR4 — scheduled Set auto

Choose near-future transitions and test Day -> Night and Night -> Day separately.
Also test screen-off/on across a boundary.

Acceptance: one required semantic transition per boundary, callback confirmation,
no continuous rewriting and correct recovery after a missed/sleep interval.

## Stage BR5 — coexistence

With safe proven values test:

- module brightness disabled versus enabled;
- stock SystemUI slider / CarSetting;
- headlights/ILL on/off;
- reverse-camera entry/exit;
- ordinary launcher/app use.

Acceptance: disabled module does not fight stock writers. The feature remains on
258/516 semantic authority and does not mutate Android brightness/sysfs/panel
calibration as a fallback.

# Combined lifecycle qualification

Only after compact, nav and brightness each pass independently, test the intended
combined feature set in order:

1. normal launcher/app use;
2. proven SystemUI restart;
3. launcher restart;
4. warm Android reboot;
5. cold boot/full power removal where safe;
6. repeated ACC sleep/wake;
7. reverse camera;
8. phone call/audio route changes;
9. projection/immersive mode;
10. representative long-duration drive/standby interval.

Acceptance:

- exactly one module-owned nav group;
- no duplicate media command;
- brightness re-establishes identity/transport/state before any mutation;
- intended settings survive where designed;
- both behavioural kill switches and `ts18_sysui` Magisk recovery remain usable;
- no repeated SystemUI exception/ANR or vehicle-UI regression.

# STOP evidence

On failure save the dashboard diagnostics plus the smallest physical reproduction
and disable only the affected layer.

Do not respond to a STOP by:

- broadening SystemUI hash/topology matching;
- widening LSPosed scope;
- reducing vertical media targets below 56dp;
- hiding/replacing OEM controls;
- introducing a second media authority;
- replacing/resigning SystemUI;
- changing Android partitions;
- using brightness level 0;
- automatically substituting Android brightness/sysfs.

If module 258/516 state is callback-confirmed but physical brightness still does
not change, collect a narrow marker-assisted stock-vs-module trace before
reconsidering the actuator.

Only after the applicable stages pass may the build be called physically
qualified. Until then use `source-validated`, `CI-validated` and `physical TS18
validation outstanding` precisely.
