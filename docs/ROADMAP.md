# Roadmap

The controlling exact-device plan is `EXACT-TS18-SYSTEMUI-FINALISATION.md`, with
the current nav/brightness correction specified in
`EXACT-APK-NAV-BRIGHTNESS-CORRECTION.md`.

Current physical evidence is asymmetric: compact collapsed touch is working on
the exact unit, while the previous right-nav media and brightness implementations
did not produce their intended effects. Exact supplied privileged APK analysis
now identifies concrete corrections rather than requiring another speculative
compatibility pass.

| Workstream | Source status | Physical status |
|---|---|---|
| exact SystemUI contract + pre-arm verifier | retained | installed hash recheck required each build |
| exact CarSetting brightness contract | added; exact hash-gated | installed hash/readback verification required |
| 43dp geometry + visual RROs | retained | currently separate from this correction |
| exact Android-Q collapsed touch | physically successful path preserved | **working; regression recheck required** |
| exact Topway nav group | corrected to `navbar_home/navbar_back/navbar_history/...`; live direct-child diagnostics added | new build validation required |
| stock nav visibility lifecycle | retained | reverse/DVR/fullscreen validation required |
| existing-session media transport | retained | app/session behaviour unverified until nav injects |
| brightness physical actuator | corrected to exact CarSetting `Settings.System.SCREEN_BRIGHTNESS` 30..255 | BR0–BR5 required |
| Topway mode transaction | corrected to observed `258,1,<mode>` then `258,128` | callback/visible validation required |
| Topway 516 | retained as observation-only | Auto effective-state validation required |
| physical brightness confirmation | readback-based with bounded retry/breaker | hardware convergence unverified |
| diagnostic console/dashboard | expanded to semantic/physical split + nav child names | exact-unit export required |
| host/JUnit/CI/package guardrails | expanded for exact correction | not physical proof |

## Current source boundary

The compact path remains intentionally unchanged because it is the one behavioural
feature physically confirmed working.

For nav, the key correction is exact resource identity rather than a new host or
broader hook: `NavigationBarView`/`navbar_left` remain correct, but the supplied
SystemUI uses `navbar_home`, `navbar_back`, `navbar_history`,
`navbar_volume_plus`, `navbar_volume_reduce`, optional `navbar_guanping` and
`navbar_app`. Unknown direct children remain a STOP.

For brightness, exact CarSetting evidence supersedes the prior assumption that
516 should be the normal physical output path. The active stock slider uses
`SCREEN_BRIGHTNESS`; 258 remains mode transport and 516 remains useful semantic
observation. Managed physical output therefore uses a single exact-gated
Settings backend with readback confirmation, while mode uses the complete
observed two-stage 258 transaction.

## Before physical release qualification

1. Complete all repository checks and address review/CI findings on the final
   correction head.
2. Install a diagnostic/release-compatible build without changing LSPosed scope.
3. Re-confirm exact SystemUI and CarSetting hashes before managed mutation.
4. Recheck the already-working compact path for regression only.
5. Capture exact navbar direct-child names/preflight before enabling media.
6. Qualify Play/Pause alone before all three media actions.
7. Run brightness BR0 stock-slider/raw observation before any managed physical
   write.
8. Qualify fixed Day/Night, stock Auto effective-state handling, then local
   scheduled transitions.
9. Test CarSetting/ILL/reverse/fullscreen coexistence and independent disable/
   recovery paths.
10. Record SystemUI restart, reboot, cold boot and repeated ACC sleep/wake with
    the combined intended feature set.
11. Keep logical brightness level 0 disabled.

Future vendor-media or alternate-brightness work remains evidence-gated. Do not
add a second media dispatch path or second physical brightness authority merely
to bypass a STOP.
