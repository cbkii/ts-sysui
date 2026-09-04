# Exact TS18 SystemUI finalisation contract

## 1. Scope and outcome

This is the controlling source-level contract for `ts-sysui` on CB's exact
Topway TS18 Android 10/API29 unit. Current physical evidence proves the compact
collapsed-shade restriction works. The first installed nav/brightness
implementation did not; exact supplied SystemUI/CarSetting reverse-engineering
now supplies the correction contract in
`EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md`.

The project remains systemless, reversible, independently disableable and
fail-open. It must not replace/resign `SystemUI.apk`, write protected Android
partitions, hook `system_server`, broaden LSPosed beyond main
`com.android.systemui`, create another media authority, or add a second physical
brightness actuator.

Source/static/CI completion is not physical TS18 qualification.

## 2. Evidence order

Use, in order:

1. present exact-device runtime/physical evidence;
2. current repository source/tests/configuration;
3. exact supplied/current TS18 privileged binaries and reproducible derived
   contracts;
4. retained exact-device captures;
5. same-firmware Topway components;
6. comparable TS10/UIS/FYT material only as secondary precedent; and
7. AOSP Android 10 where it matches the exact target.

Current physical evidence overrides old source assumptions. Current exact APK
analysis overrides earlier incomplete static interpretations.

## 3. Exact binary identity

### SystemUI mutation gate

| Property | Contract |
|---|---|
| Installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Package | `com.android.systemui` |
| Android/API | Android 10 / API 29 |
| Shared UID | `android.uid.systemui` |
| APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| Platform certificate SHA-256 | `AA:6F:9F:B3:07:05:12:AC:96:24:25:79:7C:D6:5A:A5:85:CF:62:02:93:7E:E3:CE:EF:B1:4B:58:02:EA:BD:F3` |

### Brightness actuator gate

Managed brightness also requires the exact supplied/current:

| Property | Contract |
|---|---|
| Package | `com.dofun.carsetting` |
| APK SHA-256 | `06060263e3968a4203c6c37efe95858cd959ac39481dc133de576023b7de2b71` |
| Active physical setting | `Settings.System.SCREEN_BRIGHTNESS` |
| Observed raw range | `30..255` |

Both hashes must be verified off the SystemUI main thread. A changed SystemUI,
CarSetting, class/member/resource or topology contract means the affected
mutation stays stock/off. Do not broaden matching.

## 4. Independent authority layers

1. **Framework geometry RRO** — status-bar height only.
2. **SystemUI visual RRO** — reviewed icon/clock allow-list only.
3. **SystemUI-scoped LSPosed behaviour** — optional compact touch, nav-media
   client and brightness policy/bridge.
4. **Stock Topway SystemUI/TWSystemUI** — OEM nav, panel visibility and Topway
   mode transport.
5. **Exact CarSetting physical brightness contract** — active slider semantics
   reproduced through Android `SCREEN_BRIGHTNESS` from exact-gated SystemUI.
6. **Existing Android MediaSessions** — sole media playback authority.

No one feature failure may disable another domain.

## 5. Exact collapsed touch

The physically proven path remains one persistent module-owned
`OnComputeInternalInsetsListener` targeting Android-Q
`StatusBarTouchableRegionManager`, ordered after stock lifecycle changes.

Exact mutation is allowed only when the exact SystemUI gate, configuration,
attachment, coordinate mapping and special-state checks pass. In ordinary
collapsed state it may explicitly set `TOUCHABLE_INSETS_REGION` to the bounded
strip. `mShouldAdjustInsets=true`, expanded shade, keyguard/bouncer, heads-up,
bubbles or ambiguous state stays entirely stock.

The strip remains >=64 physical px from both top corners, <=20% full physical
width and excludes the current right-system inset. Configuration may only narrow
it/increase the gap.

## 6. Geometry and visuals

The framework RRO is the only status-bar height authority. The SystemUI visual
RRO may override only the reviewed status resource allow-list in
`reference/exact-ts18-systemui-resource-matrix.md`. Never change nav geometry,
shared-risk resources or recursively scale View trees to make a build pass.
Overlay/idmap rejection is a clean STOP.

## 7. Exact Topway right navigation

The exact host remains the vertical weighted
`com.android.systemui:id/navbar_left` inside exact `NavigationBarView`.

The exact supplied SystemUI resource names are:

Required:

```text
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce
```

Known optional:

```text
navbar_guanping
navbar_app
```

The prior generic names `home`, `back`, `recent_apps`, `app` were wrong for this
binary and caused preflight failure. They must not return as exact-match aliases.

Unknown or duplicate direct children remain a STOP. Diagnostics must report the
actual live direct-child resource names, visibility and preflight reason.
Preserve current runtime order, every OEM View, ID, listener and `LayoutParams`.
Never reconstruct or replace the strip.

One owner-tagged weighted media group may contain a unique configured subset/order
of `previous,play_pause,next`, only after exact identity/topology/measurement
preflight. Vertical projected cells remain >=56dp; the existing host width must
remain >=48dp and is never widened by this module.

## 8. Stock nav visibility lifecycle

Topway navigation visibility remains stock/vendor authority. If the exact root
or `navbar_left` host is detached, hidden or not shown, remove/suspend the owned
media group and stop media observation. On return, run the complete preflight
again. Do not add reverse/MCU hooks to force visibility.

## 9. Media authority

Right-nav media controls use only:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

No MediaSession/service/queue/notification/audio-focus owner or vendor-media
fallback is introduced. Controller selection remains deterministic/sticky while
valid. Unsupported actions remain disabled. One accepted tap dispatches at most
one transport operation.

## 10. Exact brightness authority

### 10.1 Physical output

Exact supplied `CarSetting.apk` analysis supersedes the earlier assumption that
Topway command 516 is the ordinary physical brightness actuator for this build.
The active CarSetting slider writes:

```text
Settings.System.SCREEN_BRIGHTNESS
```

The module exposes logical managed levels `1..10` and maps them monotonically to
raw `30..255`:

```text
1=30, 2=55, 3=80, 4=105, 5=130,
6=155, 7=180, 8=205, 9=230, 10=255
```

Level 0 remains blocked. `-1` means preserve current.

Physical confirmation is the readback of the same Android setting. A policy
acknowledgement, Topway callback or successful reflection call is not physical
success.

### 10.2 Topway mode transaction

Topway command 258 remains mode authority (`0=Auto`, `1=Day`, `2=Night`). The
exact CarSetting mode setter performs:

```text
write(258, 1, selectedMode)
write(258, 128)
```

Both stages are reproduced on the SystemUI main looper after stock transport is
ready. Do not invent a private semantic name for the second stock operation.
Mode confirmation remains the observed 258 callback/state.

### 10.3 Role of command 516

Command/callback 516 remains a query/observation surface. Its callback can expose
packed Day/Night slots and effective Day/Night state. The module does not use an
ordinary three-argument 516 write for physical output and does not treat a 516
callback as physical brightness confirmation.

### 10.4 Mode policies

- **Day**: converge mode 1, then apply configured Day physical level if managed.
- **Night**: converge mode 2, then apply configured Night physical level if
  managed.
- **Set auto**: choose Day/Night from local clock, converge that mode, then apply
  the corresponding managed physical level.
- **Auto (stock)**: keep Topway mode 0 authoritative. If managed Day/Night levels
  are configured, wait for valid 516 effective Day/Night observation before
  choosing the physical level. Unknown effective state fails open.

### 10.5 Confirmation and breaker

No first 258 query/write occurs until `TWSystemUI.init()` completes or a valid
Topway callback proves transport readiness. Mode and physical actions share a
bounded query/read-before-retry discipline but use different confirmation:

- mode -> 258 callback/state;
- physical -> `SCREEN_BRIGHTNESS` readback.

At most one controlled retry is allowed. Repeated non-convergence opens only the
brightness breaker. Breaker cleanup removes module-owned receivers, observers,
queued work and worker resources without altering stock state.

Do not add sysfs, Factory Backlight Current, panel calibration, command 33281,
CarSetting LSPosed scope or another physical brightness authority without new
exact evidence.

## 11. Dashboard, diagnostics and recovery

The normal dashboard and diagnostic variant must separate semantic and physical
state. At minimum report:

- exact SystemUI and CarSetting contract state;
- nav hook/root/host/direct-child names/preflight/measurements;
- media controller/action state;
- Topway 258 mode and 516 observation state;
- physical backend and raw current/requested `screen_brightness`;
- both 258 transaction stage timestamps/status;
- physical read/write timestamps and convergence result; and
- independent nav/brightness breaker state.

Persistent feature switches remain independent. LSPosed disable plus reboot is
the broad behavioural recovery path. Root helpers remain bounded engineering/
recovery fallbacks, not the normal product UX.

## 12. Required repository verification

The final correction head must pass:

- shell syntax/policy;
- exact SystemUI and CarSetting repository-safe fixtures;
- exact touch/nav source contracts;
- proprietary-artifact exclusion;
- visual overlay allow-list;
- remediation and diagnostic contracts;
- Xposed compile/APK contracts;
- release tooling;
- source-manifest integrity;
- Gradle wrapper integrity;
- JVM tests;
- debug/release/diagnostic compile and Lint;
- geometry/visual/LSPosed assembly; and
- debug/diagnostic packaging contracts.

Inspect complete CI logs and address task-owned failures. Green CI is not
physical proof.

## 13. Physical qualification

Follow `docs/VALIDATION.md`. The correction must at minimum re-establish:

1. stock/recovery baseline;
2. RRO rendering;
3. LSPosed inert state;
4. compact-touch regression (already physically proven in prior build);
5. exact navbar direct-child observation;
6. Play/Pause no-session and active-session tests;
7. Previous/Play-Pause/Next;
8. nav hide/reverse/fullscreen lifecycle;
9. brightness raw stock-CarSetting observation;
10. fixed Day mode + physical readback/visible change;
11. fixed Night mode + physical readback/visible change;
12. stock Auto effective-state handling;
13. local scheduled transitions;
14. CarSetting/ILL/reverse coexistence;
15. SystemUI/reboot/cold-boot/ACC lifecycle; and
16. combined long-duration qualification.

## 14. STOP conditions

Keep the affected feature stock/off rather than improvising if:

- installed SystemUI or CarSetting hash differs;
- any required private member/type differs;
- physical coordinate mapping is ambiguous;
- overlay/idmap rejects the design;
- an unknown navbar topology/control appears;
- stock nav is hidden/detached/not shown;
- projected nav size is unsafe;
- a media tap can duplicate commands;
- Topway 258 mode semantics/transport readiness differ;
- stock Auto effective Day/Night state is unknown while managed levels need it;
- `SCREEN_BRIGHTNESS` write/readback does not converge;
- readback converges but the physical panel does not visibly change;
- reverse/vehicle behaviour is being fought; or
- rollback/ownership cannot be established.

A readback-converged/no-visible-change result requires a narrow stock CarSetting
vs module trace before reconsidering actuator authority.

## 15. Definition of source-complete

This correction is source-complete when:

- the physically proven compact path remains unchanged;
- navbar preflight uses the exact supplied `navbar_*` child IDs and reports live
  child names;
- existing MediaController remains sole media authority;
- managed physical brightness uses exact-gated `SCREEN_BRIGHTNESS` writes with
  readback confirmation;
- mode changes reproduce both exact observed 258 transaction stages;
- 516 is observation-only for this module;
- stock Auto never guesses effective Day/Night;
- dashboard/diagnostics expose the complete semantic/physical distinction;
- docs match current exact evidence;
- all final repository checks pass; and
- no proprietary binaries, decoded OEM material, generated APKs, logs,
  credentials or temporary workflows are tracked.
