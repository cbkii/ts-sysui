# Build and validation status

Updated for the v0.3 hardening branch on 17 August 2026.

## Repository-owned source checks

The repository provides and CI runs:

- shell syntax checks for host and Android shell scripts;
- `tools/test-geometry.sh` for geometry, coordinate-space, collapsed-state and
  visual-ownership pure policies;
- `tools/test-xposed-stubs.sh` for the declared legacy Xposed compile contract;
- Gradle unit tests for geometry/state/coordinate/visual policy;
- Android Lint for both APK modules;
- debug/release APK assembly as appropriate;
- `tools/test-apk-contract.sh` to ensure local Xposed stubs remain compile-only;
- packaging with checksums; release packaging additionally verifies APK signatures.

The Gradle wrapper is pinned to Gradle 8.9 and its distribution checksum is
committed. CI uses the wrapper rather than selecting an independent Gradle version.

## Evidence classes

A successful GitHub `Build` run proves host checks, unit tests, Lint, Android
compilation/assembly and debug packaging for that exact commit. It does **not**
prove Magisk RRO activation, SystemUI runtime behaviour, LSPosed compatibility on
the installed framework, touch pass-through or physical lifecycle behaviour.

Physical acceptance remains the staged sequence in `VALIDATION.md`.
