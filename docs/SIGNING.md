# Signing

Use one durable release signer for all three APK packages once device qualification
moves beyond disposable debug builds. Do not commit private keys or passwords.

`Manual Release` runs in the GitHub `release` environment and expects these exact
secret names (repository-level secrets with the same names are also compatible):

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow validates all four before any release build, strips harmless
whitespace from `KEYSTORE_BASE64`, decodes the keystore under the runner temporary
directory with mode 0600, verifies the configured alias, derives the certificate
SHA-256 fingerprint, and then proves the geometry RRO, visual RRO and LSPosed APK
use that same signer. Signing material is removed in an `always()` cleanup step.

The Gradle build still receives its existing internal `TS18_KEYSTORE_*`
environment variables, but those are populated from the standard `KEYSTORE_*`
GitHub secrets above. There is no requirement to create `TS18_KEYSTORE_*` GitHub
secrets.

## Manual release versioning

The release version is selected by `tools/resolve-release-version.sh` and
`version.properties` remains the canonical source for both APK and Magisk package
metadata.

- Leave `version_tag` blank to reuse coherent untagged release metadata after an
  interrupted run, or otherwise increment the highest semantic release tag by
  one patch version.
- If the repository has no semantic release tag yet, the coherent version already
  declared in `version.properties` is used for the initial release.
- A new explicit `vMAJOR.MINOR.PATCH` may be supplied, but it must be newer than
  the highest existing semantic tag.
- `versionCode` is monotonic and increments independently of the semantic patch
  number. Existing prepared metadata is reused rather than consuming another
  code after an interrupted pre-tag release.

For a new release the workflow **overwrites `version.properties` with the resolved
version/versionCode**, regenerates `SOURCE_MANIFEST.sha256`, creates a local
release-metadata commit, then runs the complete tests/lint/signed build/package
validation. Only after those checks pass does it push the validated metadata
commit back to the default branch. It refuses that push if the remote branch
moved during the run.

The immutable release tag is then created at that validated source commit and the
GitHub Release is created or updated from `dist/*`. Existing files of the same
name are replaced on an exact rerun. Build/CI success remains separate from
physical TS18 validation.
