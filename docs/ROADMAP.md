# Roadmap

The controlling SystemUI implementation plan is
`EXACT-TS18-SYSTEMUI-FINALISATION.md`. Current milestone status:

| Workstream | Source status | Physical status |
|---|---|---|
| exact contract + pre-arm verifier | implemented | installed hash recheck required |
| 43dp framework geometry RRO | retained/hardened | unverified this milestone |
| exact SystemUI visual allow-list/RRO | implemented | idmap/render unverified |
| exact Android-Q collapsed touch adapter | implemented | touch routing unverified |
| explicit compatibility touch adapter | retained, non-default | diagnostic only |
| exact weighted Topway nav group | implemented | measurement/reflow unverified |
| existing-session media transport | implemented | app/session behaviour unverified |
| exact Topway brightness 258/516 adapter | implemented, mutation off by default | BR0–BR5 unverified |
| scheduled Day/Night brightness policy | implemented | timed transitions unverified |
| independent recovery/configuration | implemented | recovery drill unverified |
| host/JUnit/CI/package guardrails | implemented | not physical proof |
| docs/evidence labels | implemented | maintain with fresh evidence |

## Brightness 0.5 milestone

The brightness implementation is integrated as an independently gated capability
of the existing SystemUI-scoped LSPosed APK. Its exact contract, configuration,
rollback and safety boundaries are in `BRIGHTNESS-CONTROLLER.md`.

Software scope includes:

- Topway command/callback `516` as separate Day/Night stored levels;
- Topway command `258` as Auto/Day/Night mode;
- Auto, Day, Night and local-clock **Set auto** modes;
- optional managed levels 1..10, with level 0 still blocked;
- exact-SystemUI hash and stock-transport lifecycle gates;
- one-variable-at-a-time reconciliation and bounded re-query;
- an independent process circuit breaker and persistent enable switch;
- sender-permission-protected configuration with a private Binder result callback;
- a bounded root configuration/recovery helper.

This is source/CI qualification only until BR0–BR5 in `VALIDATION.md` are run on
the exact TS18.

## Before physical release qualification

1. Complete all repository checks on the final integrated head and address every
   actionable review/CI finding.
2. Execute the staged physical matrix in `VALIDATION.md` without skipping from
   source success to all-controls enablement.
3. Record overlay/idmap, touch-region, navbar measurement, media selection and
   brightness BR0–BR5 evidence separately.
4. Record SystemUI restart, launcher restart, reboot, cold-boot and ACC evidence
   with the intended combined feature set.
5. Keep managed brightness level 0 disabled until its separate timed recovery
   gate is safely qualified.
6. Keep Android 16, generic TS18/TS10, private Topway media commands and
   `system_server` changes out of this milestone.

Future research may add a separately isolated vendor-media adapter only if an
important exact target app proves not to expose an Android MediaSession. It must
never guess command numbers or run alongside the public controller path for one
tap.
