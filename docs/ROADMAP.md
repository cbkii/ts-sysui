# Roadmap

The controlling implementation plan is
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
| independent recovery/configuration | implemented | recovery drill unverified |
| host/JUnit/CI/package guardrails | implemented | not physical proof |
| docs/evidence labels | implemented | maintain with fresh evidence |

## Before release qualification

1. Complete all repository checks on the final head and address every actionable
   review/CI finding.
2. Resolve integration order with the separate brightness-controller pull
   request before assigning a version; that work already uses 0.5.0.
3. Execute the staged physical matrix in `VALIDATION.md` without skipping from
   source success to all-controls enablement.
4. Record overlay/idmap, touch-region, navbar measurement, media selection,
   restart/reboot/cold-boot and ACC evidence.
5. Keep Android 16, generic TS18/TS10, private Topway media commands and
   `system_server` changes out of this milestone.

Future research may add a separately isolated vendor-media adapter only if an
important exact target app proves not to expose an Android MediaSession. It must
never guess command numbers or run alongside the public controller path for one
tap.
