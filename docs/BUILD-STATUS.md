# Build/validation status

Prepared: 17 August 2026.

## Static/source checks run for v0.2.0

- `bash -n tools/*.sh` — **PASS**.
- POSIX `sh -n` on Magisk/device shell scripts — **PASS**.
- `tools/test-geometry.sh` pure-Java policy/invariant suite — **PASS**.
  - exact 1280/55 case resolves to x=960..1216;
  - width is 256 px, exactly 20% of 1280;
  - top-right clearance is 64 px;
  - requests above 20% are capped;
  - configured corner gaps below 64 px are clamped upward;
  - representative width/inset/fraction sweeps assert both corner clearances and
    the 20% maximum for every valid result;
  - impossible small surfaces return invalid so runtime code can fail open.
- `javac` compilation of all LSPosed Java sources against a minimal Android 10/
  Xposed compile-stub surface — **PASS**.
- GitHub workflow YAML parse — **PASS**.
- XML well-formedness checks for manifests/resources — **PASS**.
- stale-policy search (`preserve_clickables`, old x=980..1225 geometry, old 50%
  cap) — **PASS**, no live source/config references remain.
- repository binary/private-key scan — **PASS**; no proprietary APKs, generated
  release archives or signing keys are included.

## Supplied artefact provenance used

The source package now follows the user's supplied rename mapping rather than
assuming exporter filenames are original partition basenames. In particular,
`android.overlay.sysbar_720x1280_10.apk` is treated as the export-renamed exact
active sysbar RRO associated with
`/product/overlay/framework-res_sysbar_rro_1280x720.apk`, and
`Android System_10.apk` is treated as the export-renamed
`/system/framework/framework-res.apk`.

The latter internally identifies as package `android` and has no `classes.dex`;
therefore it remains distinct from the executable `com.android.systemui`
package regardless of export filename.

## Not run here

A real Android Gradle build was **not** run in this packaging environment because
it has no Android SDK/Gradle installation. The repository's `Build` workflow is
configured to install Android SDK 35, use JDK 17 / Gradle 8.9, run the unit tests,
and assemble both debug APKs on the first GitHub push.

No physical TS18 behaviour has been claimed. Follow `VALIDATION.md` after CI is
green, one component at a time.
