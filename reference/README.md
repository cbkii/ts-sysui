# Reference material policy

Do not commit proprietary firmware APKs here. Put local copies under
`reference/apks/` if needed; Git ignores them. Commit hashes and reproducible
derived findings only.

`exact-ts18-systemui-contract.json` is the repository-safe machine-readable
contract derived from the user-supplied exact Android-10 `SystemUI.apk` analysis.
It is not a substitute for hashing the currently installed APK. Run the bundled
root-side verifier before arming an exact private adapter.
