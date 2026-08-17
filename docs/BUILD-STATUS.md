# Build and validation status

Updated for the v0.4 right-nav observation branch on 17 August 2026.

## Repository-owned source checks

The repository provides and CI runs:

- shell syntax checks for host and Android shell scripts;
- `tools/test-geometry.sh` for compact geometry, coordinate-space,
  collapsed-state and visual-ownership pure policies;
- `tools/test-nav-observation-contract.sh` to prevent the observation milestone
  from adding right-nav mutation/media-control authority or coupling its breaker
  to the compact status-bar runtime;
- `tools/test-xposed-stubs.sh` for the declared legacy Xposed compile contract;
- `tools/test-source-manifest.sh` for tracked-source integrity;
- Gradle unit tests for compact policies plus right-nav action/order and layout
  capacity/overlap policies;
- Android Lint for both APK modules;
- debug/release APK assembly as appropriate;
- `tools/test-apk-contract.sh` to ensure local Xposed stubs remain compile-only;
- packaging with checksums; release packaging additionally verifies APK signatures.

Gradle 8.9 and its distribution checksum are pinned. The generated
`gradle-wrapper.jar` is not committed or trusted blindly: the bootstrap downloads
the Gradle v8.9.0 wrapper JAR and verifies its published SHA-256 before any
`./gradlew` execution.

## Evidence classes

A successful GitHub `Build` run proves host checks, unit tests, Lint, Android
compilation/assembly and debug packaging for that exact commit. It does **not**
prove Magisk RRO activation, SystemUI runtime behaviour, LSPosed compatibility on
the installed framework, touch pass-through, right-nav hierarchy shape, safe
right-nav free space, media control authority or physical lifecycle behaviour.

The v0.4 right-nav implementation is observation-only. Physical acceptance
remains the staged sequence in `VALIDATION.md`; clickable right-nav controls
remain blocked by `RIGHT-NAV-MEDIA-ROADMAP.md` evidence gates.
