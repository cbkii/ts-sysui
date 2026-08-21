# Exact TS18 SystemUI finalisation roadmap

## 1. Purpose and outcome

This is the controlling development roadmap and implementation prompt for the
next `ts-sysui` architecture milestone. It replaces the obsolete assumption
that the executable TS18 `SystemUI.apk` is unknown and defines the work needed
to move from generic Android-Q observation hooks to evidence-gated exact-TS18
adapters.

The target is CB's exact Topway TS18 on Android 10/API 29. The milestone must:

- retain the framework RRO as the only status-bar height authority;
- use the exact SystemUI touch-region authority for collapsed input;
- replace recursive status-view scaling with resource-driven visuals;
- add optional Previous, Play/Pause and Next controls to the recognised Topway
  right-navigation host without replacing any OEM function;
- control only an existing Android `MediaController`;
- remain systemless, independently disableable, observation-first and fail-open;
- leave physical TS18 qualification explicitly unverified until it is actually
  run on the unit.

This work starts from `main` at
`f72dd27b3c3166b31f40687d6ac648ea36fb9687`. Re-resolve `main` and any active
overlapping pull requests before each consequential write. The open brightness
controller work is a separate concurrent subsystem and must not be copied,
rewritten or silently superseded by this branch.

Release numbering is deliberately deferred. Another active branch already uses
`0.5.0`; select the next non-conflicting version only after branch integration
order is known.

## 2. Evidence and authority

Use evidence in this order:

1. fresh runtime evidence from the exact installed SystemUI process and its
   namespace;
2. current repository source and tests;
3. the supplied exact TS18 Android-10 APK corpus and reproducible derived
   contract fixtures;
4. retained exact-device project captures;
5. the Android-10 UIS7862/FYT/SYU SystemUI only as secondary implementation
   precedent;
6. AOSP Android 10 source where it matches the exact contract.

Do not use the Android-16 SystemUI as an API29 implementation contract. Do not
commit proprietary APKs, decoded layouts, decompiler output or copied OEM
source. Commit only hashes, signatures, resource/member names, bounded derived
fixtures and reproducible validation code.

### Exact binary identity

The supplied conversation records the exact executable target as:

| Property | Contract |
|---|---|
| Installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Package | `com.android.systemui` |
| Version/API | Android 10 / versionCode 29 / API 29 |
| Shared UID | `android.uid.systemui` |
| APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| Platform certificate SHA-256 | `AA:6F:9F:B3:07:05:12:AC:96:24:25:79:7C:D6:5A:A5:85:CF:62:02:93:7E:E3:CE:EF:B1:4B:58:02:EA:BD:F3` |
| Process authority | SystemUI already holds `android.permission.MEDIA_CONTENT_CONTROL` |

The hash and static member/layout findings are inherited from the user-supplied
exact-APK analysis. Before a mutating adapter is armed, a repository fixture and
root-side verifier must re-establish the installed hash. A mismatch means:

```text
STOP: unsupported SystemUI contract
```

No exact private hook, visual mutation or navbar injection may run after that
result. Collect the changed APK instead of guessing.

### Present, historical and unverified facts

| Claim | Status | Consequence |
|---|---|---|
| Android 10/API 29 target | Established exact-device evidence | Compile/runtime code remains API29 compatible. |
| Historical display is 1280x720 with about 55 px top/right regions and density override 153 | Historical runtime baseline | Never hardcode it as current geometry; measure at runtime. |
| Exact SystemUI contains `StatusBarTouchableRegionManager` and the Topway navbar classes/layouts listed below | Exact-APK static finding from supplied analysis | May define a reflective contract, but every member is checked before mutation. |
| Installed APK still matches the recorded exact hash | Unknown until pre-arm check | Mutation remains off until verified. |
| SystemUI visual RRO is accepted by current overlay/idmap policy | Unknown | Build and package it independently; rejected overlay stays stock. |
| Weighted reflow keeps all relevant controls at least 56 dp on the unit | Strong static/runtime inference, not physical proof | Runtime preflight plus physical qualification required. |
| MediaController selection and commands behave correctly on the unit | Unverified | Feature defaults off and progresses Play/Pause first. |

## 3. Preserved v0.4 guarantees

Do not reimplement or weaken these existing controls:

- first SystemUI execution is observation-only;
- compact master, input and visual mutation defaults are off;
- compact policy generation prevents stale settings from arming new code;
- configured touch width remains 1-20%, with a hard 20% maximum;
- physical top-corner exclusions remain at least 64 px;
- the current right-system inset is excluded;
- coordinate-space verification gates every touch mutation;
- hook installation remains transactional and idempotent;
- compact and navbar circuit breakers remain independent;
- framework geometry remains the sole status-bar height authority;
- no broad `system_server` hook is introduced;
- build, release signing, pinned actions, source manifest and APK-contract checks
  remain enforced.

## 4. Target architecture

The final architecture has three independent authorities:

1. **Framework geometry RRO** - the existing 43 dp status-bar height override;
   no navigation dimension changes.
2. **Exact-TS18 SystemUI visual RRO** - a separately packaged overlay containing
   only approved status-specific resources.
3. **SystemUI-scoped LSPosed behaviour** - exact adapters for collapsed touch and
   optional right-nav media controls, with explicit compatibility fallback only
   where documented.

The legacy Xposed bridge remains the target because it is the repository's
qualified API29 integration. Do not mix it with a libxposed entry path.

## 5. Phase 1 - contract fixtures and drift detection

Create repository-safe JSON/Markdown fixtures for the exact SystemUI contract.
Each reflected member must include its declaring class, name, parameter/return
types, required/optional status, intended use and failure behaviour.

At minimum cover:

- `com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager`;
- `StatusBarWindowController`;
- `StatusBarWindowView`;
- `PhoneStatusBarView`;
- `NavigationBarView`;
- Topway `KeyButtonView2`;
- `com.android.systemui.tw.TWSystemUI`;
- `com.android.systemui.tw.StatusBarViewInit`;
- `status_bar.xml`;
- `navigation_bar.xml`;
- `navigation_bar_app_item.xml`;
- `navbar_left` and the known stock child IDs;
- status-only versus navigation-shared dimensions.

Known exact static touch contract to verify:

```text
class  com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager
method onComputeInternalInsets(android.view.ViewTreeObserver$InternalInsetsInfo)
field  mStatusBarWindowView
field  mStatusBarHeight
field  mIsStatusBarExpanded
```

Also capture the relevant heads-up, bouncer/keyguard and bubble state accessors
only when their exact names and types are confirmed. Missing optional safety
state means leave stock behaviour; never interpret it as inactive.

Add repository checks for:

- schema and fixture consistency;
- the recorded APK/package/API/signer identity;
- accidental contract drift;
- absence of proprietary binaries and decoded artefacts.

Add a bounded root-side pre-arm verifier. It must resolve the installed APK with
package-manager-first and explicit-path fallbacks, hash one exact file under a
timeout, report the observed identity, never modify the APK, and leave all
mutation settings off on mismatch or incomplete inspection.

## 6. Phase 2 - exact collapsed touch adapter

Add `ExactTs18TouchableRegionAdapter` as the preferred input authority.

Install an exact API29 hook on
`StatusBarTouchableRegionManager.onComputeInternalInsets(...)` and run only
after stock SystemUI completes its computation. Validate the entire required
class/member contract before registering or arming mutation. Obtain the root
from `mStatusBarWindowView` rather than discovering it through a broad
WindowManager path.

Mutation is permitted only when all of these are true:

- exact installed SystemUI contract is supported;
- policy generation and compact master/input settings are armed;
- the root is attached and still owned by the current manager;
- the bar is unequivocally in its ordinary collapsed state;
- `mIsStatusBarExpanded` is false;
- keyguard/bouncer state does not require stock handling;
- no heads-up notification is pinned, going away or transient;
- no bubble touch region is active;
- the stock touchable region is the normal full-width rectangular collapsed bar;
- local x=0 maps to physical x=0 and local width equals display width;
- geometry satisfies all existing hard invariants.

Then, and only then, replace the ordinary collapsed region with the result of
`TouchStripGeometry`. Any exception, missing safety state, non-rect region,
reinflation or coordinate ambiguity leaves the stock result unchanged.

Keep the generic `TouchableInsetsHook` only as a named compatibility mode that
is disabled by default. It must never activate automatically when the exact
adapter fails. Remove it if no supported use remains.

## 7. Phase 3 - narrow root and lifecycle hooks

Re-evaluate `WindowHooks` after the exact touch adapter is present:

- remove status-root discovery from global `WindowManagerImpl` hooks when no
  remaining supported feature needs it;
- prefer exact `NavigationBarView.onFinishInflate`, attach, detach and root
  replacement lifecycle hooks for navbar ownership;
- keep every hook exact-signature, idempotent and transactionally uninstallable;
- retain weak references only;
- allow a hook failure to disable only the owning subsystem where registration
  isolation is possible.

No work in this phase may widen scope beyond the main
`com.android.systemui` process.

## 8. Phase 4 - resource-driven status visuals

Retire recursive `View.setScaleX/setScaleY` traversal from production once the
resource path is available. Delete obsolete visual traversal code and tests
only after equivalent ownership/fail-open coverage exists.

Add a separately buildable SystemUI visual RRO targeting
`com.android.systemui`. Generate and commit a resource-use matrix that
allow-lists only exact status-specific resources. Candidate categories include:

- status icon size and drawing size;
- clock text size;
- status-specific icon spacing and padding;
- exact Topway status-only child dimensions.

Aim for approximately 75% visual sizing while retaining the 43 dp framework bar
height. Never globally override `navigation_key_width`, navigation-bar width,
height, frame dimensions or another resource shown to be shared with the right
nav.

If a status-only child has no independent overlayable resource, permit one
narrow exact-ID adjustment after the recognised `PhoneStatusBarView` inflates.
Do not restore recursive scaling or copy the proprietary OEM layout unless a
separate review proves there is no narrower safe solution.

Overlay rejection must be visible in validation and preserve stock visuals. Do
not bypass overlay policy with partition writes or signing changes.

## 9. Phase 5 - exact Topway navbar host

Recognise only the exact vertical `LinearLayout` host `navbar_left` and validate
its expected stock topology before any mutation. The supplied static analysis
records stock controls for:

- screen/power;
- Home;
- Back;
- Recents;
- optional/dormant app slot;
- volume up;
- volume down.

Preserve every original View instance, ID, listener, layout authority and
`TWSystemUI`/`TWUtil` behaviour. Do not replace or recreate the strip.

Controlled proportional reflow is allowed only inside this recognised weighted
host and only after preflight proves:

- all expected stock functions remain present;
- no stock listener/command authority is replaced;
- every visible stock and media cell projects to at least 56 dp in the relevant
  dimensions;
- 48 dp remains an engineering STOP floor, not an automatic fallback;
- there is no clipping, overlap, scrolling or width change;
- removing the owned group restores a stock-only hierarchy without reconstructing
  stock children.

Insert exactly one uniquely tagged module-owned vertical weighted group. Its
weight equals the number of enabled media actions; each enabled child has equal
weight. Do not alter existing child layout parameters unless later exact
runtime evidence proves that unavoidable and rollback is exact.

Support an independently configured subset and order of:

```text
previous,play_pause,next
```

The historical 720 px host suggests about 80 px per cell with six stock plus
three media controls, or 72 px if the app slot is visible. This is a preflight
expectation only; runtime measurement is authoritative.

Reinflation, disablement, contract mismatch or safety failure removes only the
module-owned group. Duplicate callbacks must never duplicate the group.

## 10. Phase 6 - media control client

Add `NavMediaSessionRepository` using only:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

Do not create a MediaSession, playback service, queue, media notification,
audio-focus owner, database or polling service.

Controller selection must be deterministic and sticky while the selected
controller remains valid. Prefer a playing active controller that advertises
the requested action. Preserve a valid paused selection rather than hopping
between packages. When selection or action support is ambiguous, disable/dim the
action and dispatch nothing.

Dispatch exactly one operation per accepted tap:

- Previous -> one `skipToPrevious()`;
- Next -> one `skipToNext()`;
- Play/Pause -> one specific `play()` or `pause()` chosen from reliable playback
  state.

Never issue both `TransportControls` and a media-key fallback for one tap. Do
not guess Topway command numbers. Register bounded callbacks and unregister them
on controller/root replacement or feature disable.

## 11. Phase 7 - presentation

Use module-owned vectors or suitable public resources for Previous, Play, Pause
and Next. Match stock tint, padding and pressed-state styling without using the
vendor `KeyButtonView2` class unless every key/cmd side effect is understood.

- controls require meaningful content descriptions;
- Play/Pause artwork updates from callbacks, never polling;
- unsupported actions are visibly disabled and non-dispatching;
- presentation does not take ownership of stock Topway buttons.

## 12. Phase 8 - configuration and recovery

Keep compact, navbar and any future brightness policy generations independent.
Expose explicit status/configuration operations for:

- exact-contract status and installed hash;
- touch adapter mode (`exact`, explicit compatibility fallback, or off);
- compact arm/disarm;
- visual RRO status;
- navbar observation;
- navbar media arm/disarm;
- media action subset/order;
- global LSPosed kill switch.

New policy generations migrate with all mutations off. Preserve independent
recovery routes:

- disable the LSPosed module/scope;
- compact global kill switch;
- navbar kill switch and reset;
- disable the framework geometry Magisk module;
- disable the SystemUI visual Magisk module.

Never require direct `/system` or `/product` writes.

## 13. Phase 9 - required tests

Extend pure JVM, Android unit, shell-policy and contract tests for:

- exact class/member fixture validity;
- unsupported/mismatched SystemUI contract -> zero mutation;
- ordinary collapsed touch path;
- expanded shade and detached/reinflating roots;
- keyguard/bouncer;
- pinned/going-away/transient heads-up state;
- bubbles;
- malformed, empty and non-rectangular stock regions;
- physical coordinate mismatch;
- 64 px corner and 20% width invariants;
- current right-navigation inset;
- visual overlay resource allow-list;
- rejection of shared/nav-dimension overrides;
- exact Topway nav-host recognition;
- visible and `GONE` OEM app-slot variants;
- projected equal-weight cell sizing;
- insufficient width/height -> no injection;
- duplicate inflate/root replacement;
- disable and owned-view rollback;
- action parsing, subset and order;
- no controller, multiple controllers and sticky selection;
- unsupported Previous/Next;
- playing/paused transitions;
- exactly one transport call per tap;
- callback cleanup;
- independent compact/navbar circuit breakers.

Remove tests for deleted generic implementations only after their replacement
tests cover the required safety property.

## 14. Phase 10 - CI, packaging and repository health

Extend the existing build lane; do not replace or weaken it. Add:

- exact-SystemUI contract fixture validation;
- visual resource allow-list validation;
- proprietary-binary/decompiler-output scan;
- SystemUI visual RRO lint and assembly;
- packaged-artifact validation for geometry, visual and LSPosed components;
- source-manifest coverage for every committed source/fixture.

Keep Actions pinned and permissions minimal. No temporary workflow, debug APK,
captured log, signing material, decoded proprietary resource or benchmark output
may remain in the final diff.

## 15. Phase 11 - documentation correction

Update `README.md`, `AGENTS.md`, architecture, evidence limits, installation,
recovery, validation, build status and right-nav documentation so they agree
with the implemented state.

Required corrections include:

- the exact executable SystemUI is no longer described as missing;
- its hash, path, API identity, signer and static contract are recorded with
  provenance and temporal limits;
- UIS7862/FYT evidence is secondary precedent;
- Android-16 SystemUI is explicitly non-target evidence;
- `navbar_left` and the weighted Topway topology are documented;
- evidence-backed proportional reflow is permitted only with the safety gates
  above;
- SystemUI's existing `MEDIA_CONTENT_CONTROL` authority is recorded;
- static completion and physical/runtime qualification remain distinct;
- renamed overlay exports are not treated as proof of active runtime resource
  resolution.

## 16. Phase 12 - verification and physical acceptance

Run every available repository-owned source/build check on the final head:

- shell syntax and host policy checks;
- JVM/unit tests;
- Android Lint;
- debug and applicable release compilation;
- APK contract checks;
- development packaging and checksums;
- source, binary, signing-material and proprietary-artefact scans.

Fix task-owned failures. Report the exact commit and commands. CI/static success
does not prove device behaviour.

Prepare staged exact-device validation that changes one authority at a time:

1. stock baseline;
2. geometry RRO only;
3. SystemUI visual RRO only;
4. exact touch adapter with visual/nav media off;
5. one inert owned navbar group;
6. Play/Pause only;
7. Previous and Next;
8. full configured group.

Test launcher, ordinary apps with top-edge controls, inside/outside strip drags,
heads-up, expanded shade, keyguard, immersive/fullscreen, IME, reverse camera,
phone call, projection where used, and playing/paused/stopped/no-session media.
Confirm stock Home, Back, Recents, volume and power actions throughout.

Repeat immediate, SystemUI restart, launcher restart, reboot, cold boot/full
power cycle and ACC sleep/wake boundaries. Hash the installed SystemUI before
arming any exact adapter.

## 17. Definition of done

The implementation is pull-request ready only when:

- the roadmap remains accurate for the final diff;
- exact contract fixtures and pre-arm identity verification exist;
- framework geometry is the only status-height owner;
- status visuals are resource-driven, independently packaged and allow-listed;
- collapsed touch uses the exact TS18 authority;
- the 64 px and 20% requirements remain mechanically enforced;
- no broad `system_server` hook exists;
- optional navbar controls use the recognised Topway host and an existing
  `MediaController` only;
- all OEM navbar Views/functions/listeners remain OEM-owned;
- runtime preflight enforces the 56 dp minimum and 48 dp STOP floor;
- each tap dispatches at most one media operation;
- disable/reinflation leaves a stock-only navbar;
- compact, navbar, geometry and visual subsystems remain independently
  recoverable;
- all applicable checks pass on the final head;
- the complete diff contains no proprietary binary, decoded OEM file, temporary
  diagnostic, generated APK, log or signing material;
- documentation does not overstate physical validation;
- the pull request records physical TS18 work as unverified until CB runs the
  staged acceptance sequence.

## 18. Execution prompt

Use this roadmap as the task contract. Work on a fresh branch from the current
default branch, preserve concurrent work, and implement the smallest coherent
changes that satisfy each phase. Diagnose the current owning code before editing
it. Validate against current complete files, not stale snippets. Commit this
roadmap first, then make implementation commits grouped by contract fixtures,
touch adapter, visual overlay, navbar/media, tooling/tests, and documentation.

For every private member or resource, distinguish exact static evidence from a
runtime assumption. Missing or mismatched contracts always produce zero
mutation. Do not claim an unrun device state or physical lifecycle passed. Push
the scoped branch and leave a review-ready pull request; do not merge or publish
a release without separate approval.
