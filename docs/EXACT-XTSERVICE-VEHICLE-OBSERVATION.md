# Exact XTService vehicle observation and qualification

This document defines the next exact-device layer added after merged PR #8. It
uses the supplied `XTService_2022.02.17.apk` only where the recovered contract
and current installed identity can be verified. It does not broaden LSPosed
scope beyond the main `com.android.systemui` process.

## Evidence classification

### Immutable supplied APK evidence

Supplied file:

```text
XTService_2022.02.17.apk
package: com.tw.service.xt
SHA-256: 341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267
service: com.tw.service.xt.CommandService
bind action: com.tw.service.xt.CommandService.Bind
```

Recovered `ITWCommandAidl` contains the required observation/qualification
surface:

```text
getReverseStatus()
getSleepStatus()
registerTWCommandCallback(...)
unRegisterTWCommandCallback(...)
mediaPre()
mediaPlay()
mediaPause()
mediaNext()
```

Recovered `ITWCommandCallbackAidl` contains:

```text
onReverseStatus(int)
onSleepStatus(int)
onSystemVolume(int)
onVolumeStatus(int)
```

Only reverse/sleep callbacks are consumed by this repository. The complete
vendor AIDL is intentionally not copied into product code.

### Historical exact-device runtime evidence

A successful root package capture on 2026-06-18 observed:

```text
codePath=/system/priv-app/com.tw.xtservice
base APK=/system/priv-app/com.tw.xtservice/com.tw.xtservice.apk
package=com.tw.service.xt
versionName=2022.02.17
sharedUser=android.uid.system/1000
bind action=com.tw.service.xt.CommandService.Bind
```

The same curated exact-device evidence observed:

```text
persist.navibar.position=2
Settings.System.navigationbar_config=16
Settings.System.show_navigationbar=1
```

Those are historical observations, not current-state assertions. Runtime values
are re-read rather than hard-coded.

## Runtime architecture

After exact SystemUI identity resolves `SUPPORTED`, a dedicated bounded worker:

1. hashes the installed `com.tw.service.xt` APK off the SystemUI main thread;
2. verifies the exact package/service/bind action and supplied SHA-256;
3. binds explicitly to `CommandService` only when the gate passes;
4. registers the minimal callback Binder;
5. requests initial reverse and sleep state;
6. keeps timestamps and known/unknown state separate; and
7. reconnects with bounded delay after service loss.

The observer has its own process-local breaker. Repeated XTService failures do
not disable compact touch, normal right-nav code or brightness.

No additional LSPosed package is required. No vendor process is hooked.

## Reverse/sleep policy

XTService vehicle state is only an **additional module-owned nav-media veto**.
Stock `NavigationBarView/navbar_left` visibility remains an independent primary
admission surface.

When a verified callback reports reverse active or sleep active:

```text
remove/suspend the module-owned media group
stop its MediaSession observation
leave every OEM View and visibility value untouched
```

A service disconnect after a known active state retains a stale-active veto
until a fresh inactive callback is seen. Initial unknown state does not invent a
vehicle value; ordinary stock visibility still applies.

On fresh reverse-inactive or wake state, the module does not blindly restore the
controls. It reruns the existing exact SystemUI identity, stock visibility,
resource topology and measurement preflight before creating any owned View.

This layer never commands reverse, ACC/sleep, screen power, MCU, CAN or OEM nav
visibility.

## Stock navigation configuration observation

The module reads, but never writes:

```text
persist.navibar.position
Settings.System.navigationbar_config
Settings.System.show_navigationbar
```

A detected change removes current module-owned nav media and invalidates cached
preflight state. The live navbar must then pass the complete exact preflight
again. The historical values `2`, `16` and `1` are diagnostic baseline evidence
only.

The project does not infer the currently active two-button, three-button or
gestural overlay from supplied overlay APKs alone.

## Vendor-media qualification only

Normal runtime media authority is unchanged:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

There is **no automatic XTService fallback** in this PR.

The diagnostic build exposes a separate explicit qualification activity. Each
button issues at most one of:

```text
mediaPre()
mediaPlay()
mediaPause()
mediaNext()
```

The command is admitted only when:

- the build is diagnostic;
- exact SystemUI identity is supported;
- current installed XTService matches the supplied SHA-256;
- the exact service is bound and callback registration succeeded; and
- the XTService breaker is closed.

A Binder call returning without exception is recorded only as Binder-level
success. It is not evidence that playback changed, that the current source used
the command, or that a later automatic fallback is safe.

### Physical qualification sequence

For each source below, capture the diagnostic observer state, press exactly one
qualification button, and record both Binder result and physical playback effect:

1. no media/source idle;
2. ordinary Android MediaSession playback;
3. stock local music;
4. Bluetooth media;
5. radio; and
6. any other stock source observed on the exact unit.

For each source test Previous, Play, Pause and Next separately. Confirm that the
normal right-nav controls continue to use only Android `TransportControls` and
that no single user action produces both an Android and XTService command.

Automatic vendor fallback remains deferred until this exact-device matrix
establishes safe source semantics.

## Brightness chronology diagnostics

PR #8 remains authoritative for brightness mutation:

```text
physical candidate backend: Settings.System.SCREEN_BRIGHTNESS
managed raw range: 30..255
Topway 258: mode/state
Topway 516: Day/Night/slot observation only
physical confirmation: SCREEN_BRIGHTNESS readback plus visible device result
```

The diagnostic build adds a separate read-only `SCREEN_BRIGHTNESS` observer and
records changes alongside:

- last module physical-write timestamp/requested raw value;
- last observed stock Topway 258/516 write;
- last 258 callback; and
- last 516 callback.

The resulting label is deliberately a bounded temporal correlation unless the
module itself wrote the exact requested raw value. Examples include:

```text
module-screen-brightness-write
external-screen-change-near-stock-topway-write:...
external-screen-change-near-topway-516-callback
external-screen-change-near-topway-258-callback
external-or-unknown-writer
```

This instrumentation is intended to discriminate the historical runtime trace
that showed 516 activity with little/no Android brightness movement from the
later exact CarSetting static branch that writes `SCREEN_BRIGHTNESS`. It does not
resolve that discrepancy by assumption.

## STOP conditions

STOP exact XTService use for the process if:

- installed XTService SHA does not match the supplied exact APK;
- exact service/action/component cannot be resolved;
- repeated bind/register/query failures open the XTService breaker;
- callback values are outside the currently accepted 0/1 observation domain;
- diagnostic media calls show source-dependent unsafe or duplicated behaviour;
- normal MediaController dispatch and diagnostic XTService dispatch cannot be
  kept mutually exclusive; or
- device evidence shows the recovered Binder transaction contract no longer
  matches the installed service.

A STOP does not justify adding `com.tw.service.xt`, CarSetting, TWCore or
`system_server` to LSPosed scope.

## Recovery

Disable the affected nav feature or restart SystemUI to clear process-local
observer/breaker state. If the module itself causes instability, disable the
LSPosed module using the existing recovery route. No APK replacement, partition
write, service deletion or vendor-state rollback is required because this layer
owns only its connection, callbacks, diagnostic activity and module Views.

## Physical status

Repository tests and CI can validate the exact contract, transaction ordinals,
lifecycle policy, no-automatic-fallback rule and build packaging. They cannot
prove reverse/sleep semantics, Binder reachability under the exact installed
SELinux/signature context, media-source behaviour, visible brightness output,
reboot/cold-boot behaviour or ACC lifecycle. Those remain exact-device
qualification work.
