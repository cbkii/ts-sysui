# Signing

Use one durable release signer for both APK packages once device qualification
moves beyond disposable debug builds. Do not commit private keys or passwords.

`release.yml` expects four repository secrets:

- `TS18_KEYSTORE_B64`
- `TS18_KEYSTORE_PASSWORD`
- `TS18_KEY_ALIAS`
- `TS18_KEY_PASSWORD`

The workflow validates all are present before decoding the keystore, writes it
with mode 0600 under the runner temporary directory, builds/packages signed APKs,
and removes the keystore in an `always()` step **before** any release-publishing
action runs. Release packaging verifies both APK signatures with `apksigner`.

The release tag must exactly match `v<versionName>` from the canonical
`version.properties`; mismatches stop before signing/build publication.
