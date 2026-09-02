# Diagnostic build and runtime observability policy

This policy is mandatory for TS18 System UI development from the first diagnostic
build onward. It exists because source/static/CI success is insufficient evidence
that an LSPosed/SystemUI integration actually loaded or reached a private OEM
runtime path on the exact head unit.

## Build classes

The project has three relevant Android build classes:

- `release`: production candidate. Conservative logging, mutation-off defaults,
  normal fail-open behaviour.
- `debug`: developer/CI build. Verbose bounded diagnostics are compiled in and
  forced on, but the APK normally uses an environment-specific debug signer.
- `diagnostic`: release-derived, debuggable exact-device test build. Verbose
  bounded diagnostics are forced on and the `DiagnosticSettingsActivity` launcher
  is enabled. The Manual Diagnostic Build workflow signs it with the configured
  release certificate so it can replace the matching release install on the
  test unit without changing package identity. It never tags or publishes a
  GitHub Release.

A diagnostic build is not a production release and must be visibly labelled as
diagnostic in its APK version name, Magisk `module.prop`, bundle `BUILD-INFO.txt`,
and console.

## Mandatory runtime event model

Every new SystemUI-facing subsystem or lifecycle hook must report enough state to
answer, without guesswork:

1. Did the Xposed module entry point execute in the intended package/process?
2. Did installation of the relevant hook begin?
3. Did hook/reflection/resource resolution succeed or fail?
4. Which exact readiness/admission gate currently blocks mutation?
5. Was a configuration request received and validated?
6. Was a mutation attempted?
7. Was the mutation confirmed, rejected, rolled back, or left stock?
8. Did a circuit breaker open?
9. Did owned cleanup complete?
10. Did a lifecycle/reinflation/restart cause the state to change?

Use stable stage names and explicit state transitions. Prefer events such as
`INSTALLING`, `INSTALLED`, `READY`, `BLOCKED`, `ACTIVE`, `CONFIRMED`, `FAILED`,
`ROLLED_BACK`, and `CLEANED` over prose-only logging.

## Required diagnostic sinks

Diagnostic/debug builds must emit relevant module events to all safe available
sinks:

- Android logcat with tag `TS18SysUI`;
- LSPosed/Xposed log when running inside the injected process;
- the bounded in-process `DiagnosticJournal`, returned through the diagnostic
  status bridge and included in Diagnostic Console exports.

The normal APK process must remain safe when Xposed classes are absent. Logging
must never make Xposed availability a runtime dependency for the configuration
Activity.

Release builds retain the same structural events but may rate-limit repetitive
debug details.

## Boundedness and performance

Verbose does not mean unbounded.

- Diagnostic journal: maximum 512 entries and maximum 96 KiB status snapshot.
- Release journal: maximum 96 entries.
- One journal record is bounded to a short stage and detail line.
- Diagnostic hot-path debug output is rate-limited per stage to at most one
  emitted line every 250 ms.
- Release repetitive debug output remains at a 5 s scale.
- Repeated stack traces are bounded per stage.
- Never add synchronous disk I/O, hashing, package scanning, shell execution, or
  Binder-heavy diagnostics to a UI/render/touch callback.
- Never introduce persistent background polling merely for logging.
- A logging failure must be swallowed and must never become mutation authority.

If an event is too hot for the above limits, emit state transitions and aggregate
counts rather than every invocation.

## Privacy and evidence scope

Diagnostic output may include:

- module/build/version identity;
- process/package/UID/PID/thread identity;
- exact SystemUI/module APK paths and hashes;
- hook/reflection/resource names;
- resource dimensions and View IDs/bounds;
- MediaSession controller package names, playback state and advertised action
  flags;
- Topway command IDs/arguments already required for brightness diagnosis;
- breaker/failure/timeout state.

Do not log media titles, artists, file names, user-entered text, contacts,
messages, credentials, tokens, unrelated app data, or broad filesystem content.

## Bootstrap rule

The earliest possible module entry event must be emitted before risky hook
installation. Each required hook-install stage must be identified individually
before an all-or-nothing rollback occurs. Optional feature failures must also
report their own install state.

A future remediation that isolates optional hooks into independent registries is
welcome, but diagnostic observability must not regress while that work proceeds.

## RRO evidence rule

Mounted overlay APK files are not proof that an RRO is effective. Diagnostic
status should report both:

- whether the expected systemless payload path is visible; and
- the actual resource value resolved by the running SystemUI/framework process.

Idmap/overlay rejection remains a STOP and is not a reason to replace or resign
SystemUI.

## Diagnostic Console

The diagnostic build exposes a second launcher entry named **TS18 Diagnostic
Console**. It is read-only.

It must provide:

- a local APK self-test that works even if LSPosed/SystemUI IPC is completely
  absent;
- package/version/signer information;
- `assets/xposed_init` and declared scope information;
- bridge timeout/reply state;
- exact identity and hook-install state;
- effective resource dimensions;
- compact/nav/brightness state;
- circuit breakers;
- the bounded SystemUI diagnostic journal;
- copy and MediaStore Download export.

The console must not arm features or issue Topway brightness/media mutations.

## Signing and distribution

The Manual Diagnostic Build workflow:

- is dispatchable only from the repository default branch;
- uses the protected `release` environment;
- validates the same signing secrets and signer identity as Manual Release;
- builds and signs `diagnostic` variants;
- packages the same combined `ts18_sysui` Magisk module ID so it upgrades rather
  than duplicates the installed SystemUI RRO module;
- uploads a short-lived Actions artifact only;
- never changes `version.properties`;
- never commits, pushes, creates a tag, or creates/updates a GitHub Release.

PR CI still compiles/lints the diagnostic variant, but those CI artifacts use the
normal debug signer when release signing material is unavailable and must not be
treated as upgrade-compatible with a release-signed install.

## CI contract

Every change affecting diagnostics, build types, packaging or release workflows
must keep tests that prove at least:

- `release` compiles with `TS18_DIAGNOSTIC=false`;
- `debug` and `diagnostic` compile with `TS18_DIAGNOSTIC=true`;
- diagnostic uses the same application ID as release;
- no `applicationIdSuffix` silently creates a second module identity;
- the diagnostic launcher manifest exists only in the diagnostic source set;
- the journal is bounded;
- Diagnostic Console remains read-only;
- production packaging remains unchanged;
- diagnostic packaging is visibly labelled and checksum-validated;
- Manual Diagnostic Build contains no tag/release/source-push step;
- all Xposed bridge stubs remain compile-only.

Do not weaken these checks to obtain green CI.

## Physical-development rule

A diagnostic build increases observability; it does not relax physical safety.
The exact SystemUI SHA gate, API29/device gate, stock nav preservation, compact
touch geometry limits, brightness level-0 prohibition, independent breakers and
all recovery STOP conditions remain unchanged.

Any future change that deliberately relaxes one of those controls requires a
separate evidence-backed engineering decision, not a debug-build exception.
