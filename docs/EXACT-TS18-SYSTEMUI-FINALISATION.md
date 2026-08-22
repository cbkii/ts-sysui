# Exact TS18 SystemUI finalisation contract

## 1. Scope and outcome

This document is the controlling source-level contract for `ts-sysui` on CB's
exact Topway TS18 Android 10/API29 unit. It records the current architecture
after merged PR #5, merged PR #4 and the unreleased physical-0.5.1 remediation.

The project must remain systemless, reversible, independently disableable and
fail-open. It must not replace/resign `SystemUI.apk`, write protected Android
partitions, hook `system_server`, broaden LSPosed beyond the main
`com.android.systemui` process, create another media playback authority, or use
Android/sysfs brightness as a substitute for the recovered Topway semantic path.

Source/static/CI completion is not physical TS18 qualification.

## 2. Evidence order

Use, in order:

1. present exact-device runtime evidence;
2. current repository source/tests/configuration;
3. exact supplied/current TS18 Android-10 binaries and reproducible derived
   contracts;
4. applicable exact-device retained captures;
5. same-firmware Topway components such as CarSetting/TWUtil;
6. TS10/Driving/UIS7862/FYT implementations only as secondary precedent;
7. AOSP Android 10 where it matches the target contract.

Current physical evidence overrides old source assumptions. Current source
supersedes old roadmap wording. Duplicate binary copies are not corroboration.

## 3. Exact SystemUI identity

The retained executable contract is:

| Property | Contract |
|---|---|
| Installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Package | `com.android.systemui` |
| Android/API | Android 10 / API 29 |
| Shared UID | `android.uid.systemui` |
| APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| Platform certificate SHA-256 | `AA:6F:9F:B3:07:05:12:AC:96:24:25:79:7C:D6:5A:A5:85:CF:62:02:93:7E:E3:CE:EF:B1:4B:58:02:EA:BD:F3` |

Before private mutation, runtime must re-hash the installed APK off the SystemUI
main thread. Resolution callbacks that can touch Views/SystemUI lifecycle state
must be explicitly dispatched back to the main looper.

A hash/class/member/resource/topology mismatch means zero exact mutation. Do not
weaken the gate or silently select compatibility mode.

## 4. Independent authority layers

The implementation deliberately keeps these separate:

1. **Framework geometry RRO** — owns the compact status-bar height only.
2. **SystemUI visual RRO** — owns the narrow reviewed status icon/clock resource
   allow-list only.
3. **SystemUI-scoped LSPosed behaviour** — owns optional compact touch,
   right-nav media client and brightness policy/bridge.
4. **Stock Topway SystemUI/TWSystemUI/TWUtil** — continues to own OEM navigation,
   vehicle-state panel visibility, screen power and vendor transport.
5. **Existing Android media sessions** — remain playback authority.

No one feature failure may take down another feature domain.

## 5. Exact collapsed touch

### 5.1 Stock Android-Q lifecycle

The exact adapter targets
`com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager`.

Ordinary Android-Q `InternalInsetsInfo` computation starts in the default FRAME
mode with an empty `touchableRegion`. Therefore exact mode must **not** require
stock SystemUI to have already supplied a non-empty REGION before applying the
normal collapsed strip.

The production exact path uses one persistent module-owned
`OnComputeInternalInsetsListener`. Constructor and `updateTouchableRegion()`
lifecycle hooks only keep that listener ordered after stock changes. The module
does not also hook `onComputeInternalInsets()` as a second mutation path.

### 5.2 Stock ownership signal

The Android-Q stock control state `mShouldAdjustInsets` is a required runtime
reflection/type gate. The supplied exact-static fixture did not independently
prove that private field, so the repository records it accurately as an
AOSP-Q-derived control requirement that must be verified against the installed
exact binary before mutation.

If the field is missing or not boolean, exact touch does not install.

If:

```text
mShouldAdjustInsets == true
```

leave `InternalInsetsInfo` completely untouched. Stock keeps ownership of its
special touch state.

### 5.3 Ordinary collapsed mutation

Exact mutation is allowed only when all required gates pass, including:

- exact APK identity supported;
- compact/input explicitly armed;
- root attached;
- `mShouldAdjustInsets == false`;
- `mIsStatusBarExpanded == false`;
- keyguard and bouncer not active;
- no pinned/departing heads-up state;
- no bubble state requiring stock handling;
- no force-collapsed/layout transition;
- physical/local coordinate mapping proven;
- valid runtime dimensions.

Then exact mode explicitly performs:

```text
info.setTouchableInsets(TOUCHABLE_INSETS_REGION)
info.touchableRegion.set(stripLeft, 0, stripRight, barHeight)
```

The strip must remain at least 64 physical pixels from both top corners, never
exceed 20% of full physical width and exclude the current right-system inset.
Configuration can only make it smaller/further from corners.

The generic compatibility adapter remains explicit-only and preserves its older
recognised-stock-REGION safety policy. Exact failure never activates it
automatically.

## 6. Geometry and visuals

The framework RRO remains the only status-bar height authority. Do not restore a
runtime `TYPE_STATUS_BAR` height normaliser.

The SystemUI visual RRO is independently packaged and may override only the
reviewed status resource allow-list in
`reference/exact-ts18-systemui-resource-matrix.md`. Current intended resources
are status icon size/drawing size and clock size.

Do not override navigation width/height/frame resources or shared-risk resources
for convenience. Do not restore recursive `View.setScaleX()/setScaleY()` tree
scaling. Overlay/idmap rejection is a clean STOP that retains stock visuals.

## 7. Exact Topway right navigation

The current exact host is the vertical weighted
`com.android.systemui:id/navbar_left` inside the exact `NavigationBarView`.

Current physical evidence makes these direct stock controls mandatory:

- Home;
- Back;
- Recents;
- Volume+;
- Volume−.

The exact-static screen/power and app-slot controls are known optional children
and may be absent/conditional at runtime. Unknown or duplicate direct children
remain a STOP.

Preserve current runtime stock order, every OEM View, ID, listener,
`LayoutParams` and Topway command. Never replace/recreate the strip.

One owner-tagged weighted group may contain a unique configured subset/order of:

```text
previous,play_pause,next
```

It may be injected only after exact identity, topology and measured sizing
preflight. Vertical cells must remain >=56dp. The module uses the existing OEM
sidebar width; 48dp is the absolute horizontal floor, not permission to narrow
or widen the stock strip.

## 8. Stock nav visibility lifecycle

Topway navigation visibility is stock/vendor authority. The module must never
force the panel visible.

A public-View visibility monitor observes the exact `NavigationBarView` and
`navbar_left` lifecycle. If the stock root/host is detached, hidden or not shown,
the owned media group is removed and its media observer stopped. When stock
becomes visible again, start over with the full exact identity/topology/size
preflight before reinjection.

Do not add reverse/MCU hooks merely to keep module buttons alive.

Secondary 4PDA firmware history supports this fail-open design: Topway has
changed navigation-panel sorting/configuration/fullscreen behaviour, and the
December-2024 TS18 branch family records a reverse+DVR navigation-panel hide fix.
These references are precedent only, not exact current runtime proof:

- `https://4pda.to/forum/index.php?showtopic=1015856&st=28740`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=45340`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=44300`
- `https://4pda.to/forum/index.php?showtopic=1015856&st=46140`

Android navbar overlays remain a separate geometry layer. TS10/Driving/FYT
SystemUI binaries are differential research material only and must never be
transplanted onto this TS18.

## 9. Media authority

Right-nav media controls use only:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

Do not create a MediaSession, playback service, queue, notification, audio-focus
owner or vendor media authority.

Controller selection remains deterministic/sticky while valid. Unsupported
commands are disabled. One accepted tap dispatches at most one operation:

- Previous -> one `skipToPrevious()`;
- Next -> one `skipToNext()`;
- Play/Pause -> one `play()` or one `pause()`.

Never pair TransportControls with media-key fallback or guessed
`TWSystemUI.write(...)` media commands.

## 10. Brightness authority

The current exact semantic model is:

- Topway command/callback `258` = mode (`0=Auto`, `1=Day`, `2=Night`);
- Topway command/callback `516` = separate Day/Night stored 0..10 slots and
  active Day/Night condition.

Use one policy engine and one narrow adapter around the existing SystemUI
`TWSystemUI`/`TWUtil` transport. Android `screen_brightness`, backlight sysfs,
theme state, screen power and factory panel-current calibration are separate
surfaces/domains.

Supported policy modes are Auto, Day, Night and local-clock Set-auto. Managed
Day/Night levels are independent and preserve the opposite/unmanaged slot.
Managed level 0 remains blocked pending a separate timed no-backlight recovery
test.

No first query/write occurs until stock `TWSystemUI.init()` succeeds or a valid
Topway callback proves transport readiness. Reconciliation is callback-first,
changes one semantic variable at a time, performs one semantic query before a
bounded retry and opens only the brightness breaker on non-convergence.

When the brightness breaker opens, stop future writes and clean up the module's
owned time receiver, settings observer, queued work and HandlerThread. Do not
alter stock Topway state during breaker cleanup. SystemUI restart is required to
re-arm that process.

Reverse-camera brightness coexistence remains a physical evidence question. Do
not invent a private reverse signal pre-emptively; observe 258/516 during parked
reverse testing and add suspension only if current evidence proves it necessary.

## 11. Dashboard, configuration and recovery

The companion TS18 System UI Activity and the signature-protected in-process
bridge provide the normal configuration/status surface. It exposes bounded
current identity, hook/breaker, nav preflight/media-session and brightness
258/516 transport/callback/confirmation state.

Root helpers remain recovery/engineering fallbacks. Persistent feature switches
remain independent so compact input, nav media and brightness can be disabled
without removing unrelated layers. LSPosed disable plus reboot remains the broad
behavioural recovery path.

No configuration path grants the normal APK platform identity or
`WRITE_SECURE_SETTINGS`; privileged settings writes stay in the already
privileged exact SystemUI process or explicit root helper.

## 12. Required repository verification

The final remediation head must pass the repository-owned checks for:

- shell syntax/policy;
- exact SystemUI fixture and single-path exact-touch source contract;
- proprietary-artifact exclusion;
- visual overlay allow-list;
- remediation contract;
- legacy Xposed compile/APK contract;
- release-version tooling;
- source manifest;
- Gradle wrapper integrity;
- JVM tests;
- Android compile and Lint;
- geometry/visual/LSPosed APK assembly;
- APK contract;
- development packaging and packaged-artifact contract.

Inspect complete workflow logs and fix task-owned failures. A green check is
source/CI evidence only.

## 13. Physical qualification

Follow `docs/PHYSICAL-0.5.1-REMEDIATION.md` and `docs/VALIDATION.md` in staged
order. At minimum re-establish:

1. stock baseline and recovery;
2. geometry RRO;
3. visual RRO/idmap;
4. LSPosed inert;
5. corrected exact compact touch;
6. nav observation/visibility;
7. Play/Pause only;
8. Previous/Play-Pause/Next;
9. reverse/fullscreen/call/projection/nav lifecycle;
10. brightness BR0 observation;
11. BR1 Day;
12. BR2 Night;
13. BR3 scheduled transitions;
14. BR4 stock-slider/ILL/reverse coexistence;
15. BR5 SystemUI/reboot/cold-boot/ACC lifecycle;
16. combined lifecycle and long-duration qualification.

Do not claim any stage passed until exact-device evidence is supplied.

## 14. STOP conditions

Keep the affected feature stock/off rather than improvising if:

- installed SystemUI hash differs;
- any required private member/type differs, including `mShouldAdjustInsets`;
- physical coordinate mapping is ambiguous;
- overlay/idmap rejects the visual/geometry design;
- an unknown navbar topology/control appears;
- stock nav is hidden/detached/not shown;
- projected nav size is unsafe;
- a media tap can duplicate commands;
- Topway 258/516 semantics or transport readiness differ;
- brightness does not converge within bounded confirmation;
- reverse/vehicle behaviour is being fought;
- rollback/ownership cannot be established.

Firmware, MCU, panel calibration or protected package replacement remains a
separate STOP requiring explicit approval and exact recovery evidence.

## 15. Definition of source-complete

The repository is source-complete for this milestone when:

- exact touch uses one persistent post-stock listener;
- `mShouldAdjustInsets=true` leaves stock InternalInsetsInfo untouched;
- safe ordinary collapsed FRAME/empty state is explicitly converted to bounded
  REGION;
- identity completion that can touch Views runs on main;
- nav media follows stock visibility and re-preflights on return;
- existing MediaController remains sole media authority;
- brightness retains one 258/516 semantic controller and cleans up owned runtime
  resources on breaker;
- dashboard/recovery paths remain bounded and independent;
- docs match current code/evidence;
- all final repository checks pass;
- no proprietary binaries, decoded OEM material, generated APKs, logs,
  credentials or temporary workflows are tracked.

After that boundary, remaining work is physical TS18 qualification, not an
unknown source architecture. Do not merge or publish a release without separate
approval.
