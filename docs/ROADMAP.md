# Roadmap

The controlling SystemUI implementation plan is
`EXACT-TS18-SYSTEMUI-FINALISATION.md`. The current branch is the unreleased
physical-0.5.1 remediation pass after the first exact-unit install exposed no
visible right-nav media controls and no visible Day/Night brightness difference.

| Workstream | Source status | Physical status |
|---|---|---|
| exact contract + pre-arm verifier | implemented | installed hash recheck required |
| 43dp framework geometry RRO | retained/hardened | idmap/render recheck required |
| exact SystemUI visual allow-list/RRO | implemented | idmap/render recheck required |
| exact Android-Q collapsed touch adapter | corrected to one post-stock listener; runtime `mShouldAdjustInsets` gate; default FRAME/empty -> bounded REGION | corrected routing unverified |
| explicit compatibility touch adapter | retained, non-default | diagnostic only |
| exact Topway nav group | implemented with physical mandatory/optional child contract | measurement/reflow unverified |
| stock nav visibility lifecycle | implemented without reverse/MCU hook; hidden/not-shown panel suspends owned group | reverse/DVR/fullscreen validation required |
| existing-session media transport | implemented | app/session behaviour unverified |
| exact Topway brightness 258/516 adapter | implemented, mutation off by default | BR0–BR5 unverified |
| callback-first brightness confirmation | implemented with one query before bounded retry | hardware convergence unverified |
| scheduled Day/Night brightness policy | implemented | timed transitions unverified |
| brightness breaker resource cleanup | implemented | breaker/restart drill unverified |
| unified dashboard/status bridge | implemented | exact-unit UI/bridge acknowledgement unverified |
| independent recovery/configuration | implemented | recovery drill unverified |
| host/JUnit/CI/package guardrails | implemented; rerun on final head required | not physical proof |
| docs/evidence labels | updated for remediation + secondary 4PDA precedent | maintain with fresh evidence |

## Current source completion boundary

The previous P0 exact-touch defect is addressed in source by making the module's
persistent internal-insets listener the only exact mutation path. It now treats
`mShouldAdjustInsets=true` as complete stock ownership, while safe ordinary
collapsed computation may explicitly set `TOUCHABLE_INSETS_REGION` even when
Android-Q starts `InternalInsetsInfo` in FRAME/empty state. Because the supplied
exact-static fixture did not independently prove that private field, its
name/type are a mandatory runtime reflection gate; absence disables exact touch
rather than broadening the contract.

Identity completion callbacks are marshalled to the main looper before View or
SystemUI lifecycle work. Right-nav media controls are now subordinate to the
stock panel's public View visibility: hidden/detached/not-shown state removes the
owned group and stops its media client, and a visible return performs the full
identity/topology/measurement preflight again.

The brightness implementation remains one Topway semantic policy engine using
258/516 state. The remediation dashboard exposes transport/callback/pending
confirmation state, and the brightness breaker now cleans up module-owned
receivers, observer, queued work and worker thread without touching stock state.

## Secondary Topway firmware precedent

4PDA material is useful **secondary precedent only**. It does not replace the
exact TS18 binary/runtime contract. Relevant public discussions include:

- `https://4pda.to/forum/index.php?showtopic=1015856&st=44300` — Topway-specific
  `com.android.systemui.tw.*` implementation evidence;
- `https://4pda.to/forum/index.php?showtopic=1015856&st=46140` — automotive
  buttons described as SystemUI-hosted;
- `https://4pda.to/forum/index.php?showtopic=1015856&st=28740` — firmware changes
  to navigation-panel configuration/sorting/fullscreen behaviour;
- `https://4pda.to/forum/index.php?showtopic=1015856&st=45340` — the same
  December-2024 TS18 firmware family records a reverse+DVR navigation-panel hide
  fix;
- `https://4pda.to/forum/index.php?showtopic=1023106&st=4960` — Android navbar
  overlay experiments altering reserved geometry independently of Topway button
  behaviour; and
- `https://4pda.to/forum/index.php?showtopic=1015856&st=40580` plus
  `st=48720` — TS10/Driving SystemUI variants useful for structural comparison,
  never as TS18 transplant binaries.

Engineering consequence: keep framework/navbar geometry, Topway SystemUI panel
behaviour and vehicle-state visibility as separate authority layers. Never force
stock nav visible to preserve module controls, and never install a foreign
TS10/FYT/Driving SystemUI on the exact TS18.

## Before physical release qualification

1. Complete the final repository checks on the remediation head and address every
   task-owned review/CI finding.
2. Run the exact staged remediation/validation sequence without skipping from
   CI success to all-controls enablement.
3. Re-confirm installed SystemUI hash and the runtime private touch contract
   before arming exact input.
4. Record overlay/idmap, touch-region, navbar visibility/measurement/media and
   brightness BR0–BR5 evidence separately.
5. Explicitly exercise reverse, reverse+DVR where available, fullscreen,
   projection and call states while confirming the module never forces nav
   visibility or fights temporary brightness behaviour.
6. Record SystemUI restart, launcher restart, reboot, cold-boot and ACC evidence
   with the intended combined feature set.
7. Keep managed brightness level 0 disabled until its separate timed recovery
   gate is safely qualified.

Future vendor-media work remains evidence-gated: only consider a separately
isolated adapter if an important exact target app proves not to expose a usable
Android MediaSession. Never guess a Topway command or dispatch two authorities
for one tap.
