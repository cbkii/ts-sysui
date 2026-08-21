# Exact TS18 System UI

A systemless, reversible Android 10/API 29 implementation for CB's exact
Topway TS18 (`s9863a1h10`). It is not a generic TS10/TS18 modification.

The project keeps five authorities separate:

1. **Geometry** — a framework RRO reduces the OEM status-bar height to 43dp.
2. **Visuals** — an exact-SystemUI RRO changes only allow-listed status
   icon/clock dimensions.
3. **Collapsed input** — a SystemUI-scoped LSPosed exact adapter narrows only the
   ordinary collapsed shade touch region.
4. **Right-nav media** — the same LSPosed APK can add a reversible media group to
   the recognised Topway `navbar_left` host and command an existing
   `MediaController`.
5. **Brightness** — an independently gated controller uses the recovered Topway
   Day/Night mode/brightness contracts rather than Android brightness/sysfs as
   the actuator.

The implementation never replaces `SystemUI.apk`, writes an Android partition,
hooks `system_server`, creates a playback service/session/queue/notification or
takes audio focus.

## Physical 0.5.1 remediation

The first exact-device 0.5.1 installation established two important failures:
Day/Night selection produced no observable brightness change and no custom media
buttons appeared in the right sidebar. These current physical observations take
priority over the earlier static/CI assumptions.

The remediation therefore replaces hidden configuration plus silent fail-open
behaviour with a user-facing **TS18 System UI** dashboard and a
signature-protected bidirectional bridge injected into the already-privileged
SystemUI process. The dashboard reports exact identity, hook state, navbar
preflight/measurements, media-session selection, Topway brightness state and
258/516 confirmation rather than merely assuming that a saved setting worked.

See [`docs/PHYSICAL-0.5.1-REMEDIATION.md`](docs/PHYSICAL-0.5.1-REMEDIATION.md).

## Exact SystemUI gate

Behavioural mutation requires the exact current contract:

| Field | Required value |
|---|---|
| package | `com.android.systemui` |
| installed path | `/system/priv-app/SystemUI/SystemUI.apk` |
| Android/API | Android 10 / 29 |
| device token | `s9863a1h10` |
| APK SHA-256 | `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f` |
| shared UID | `android.uid.systemui` |
| media authority | `android.permission.MEDIA_CONTENT_CONTROL` |

Hashing remains off the SystemUI main thread. The dashboard bridge is allowed to
report `CHECKING`/`UNSUPPORTED`, but mutating requests remain rejected until the
identity is `SUPPORTED`.

## Hard collapsed-input boundary

An armed collapsed shade strip always:

- remains at least **64 physical pixels** from both top corners;
- is no wider than **20%** of full physical width;
- excludes the current right-navigation inset; and
- keeps stock behaviour when coordinates, stock region or special SystemUI state
  are ambiguous.

For the last-observed 1280×720 display and ~55px right nav, the maximum half-open
strip remains **[960,1216)**. Runtime geometry is authoritative.

## Right sidebar media controls

The exact SystemUI layout contains a vertical weighted `navbar_left`. Current
physical observation shows Home, Back, Recents, Volume+ and Volume−; these five
are mandatory and are never recreated or replaced. The exact static layout also
contains known screen/power and app-slot controls, which may be conditional or
`GONE`.

The remediation preflight requires:

- every mandatory stock control as a direct child;
- any present known optional controls to remain direct and unchanged;
- no unknown direct child;
- positive uniform visible stock weights and no explicit host `weightSum`;
- an attached, laid-out host;
- at least **56dp projected vertical cell height**; and
- at least **48dp existing horizontal width**, while preferring 56dp.

The module uses the full existing OEM strip width and never widens the strip just
to satisfy density rounding. Insufficient vertical room remains a STOP.

The configurable subset/order is:

```text
previous,play_pause,next
```

The media group remains **visible but disabled** when no usable media session is
present. Media authority remains strictly:

```text
MediaSessionManager -> existing MediaController -> TransportControls
```

There is no second session/service or duplicate media-key/vendor fallback.

## Topway brightness controller

Current exact-device evidence identifies:

```text
command/callback 516: separate Day/Night brightness slots, stock range 0..10
command 258: mode 0=Auto, 1=Day, 2=Night
```

The controller offers **Auto (stock)**, **Day**, **Night** and **Set auto
(scheduled)**. Managed Day/Night levels remain restricted to **1..10**; level 0
is blocked pending a separate timed no-backlight recovery test.

The remediation distinguishes policy persistence from actual device behaviour:

```text
HOOK/IDENTITY
 -> TRANSPORT_READY
 -> MODE_STATE_KNOWN
 -> LEVEL_STATE_KNOWN
 -> ACTION_PENDING
 -> CALLBACK_CONFIRMED
 -> ACTIVE/SETTLED
```

It reports current Topway mode, effective night state, stored Day/Night values,
last 258/516 callbacks, last module action, pending action and confirmation
result. If Day and Night slots are equal, the dashboard explicitly warns that a
mode change alone cannot visibly change brightness.

Confirmation is callback-first: one write is followed by a bounded wait, one
semantic query, then at most one controlled retry. Missing confirmations are
reported distinctly as `NO_258_CALLBACK` and `NO_516_CALLBACK`. A single slow
confirmation window no longer immediately opens the breaker.

See [`docs/BRIGHTNESS-CONTROLLER.md`](docs/BRIGHTNESS-CONTROLLER.md).

## Installation artefacts

Normal packaging now produces **one Magisk module**, not separate geometry and
visual modules:

```text
TS18-SystemUI-Magisk-v<version>-release.zip
TS18-SystemUI-LSPosed-v<version>-release.apk
TS18-SystemUI-Bundle-v<version>-release.zip
SHA256SUMS.txt
```

The combined `ts18_sysui` Magisk ZIP contains both independently built/tested
RRO APKs:

```text
system/product/overlay/TS18StatusBarGeometry.apk
system/product/overlay/TS18StatusBarVisuals.apk
```

If either legacy ID `ts18_statusbar_geometry` or `ts18_statusbar_visuals` is
still active, the installer stops rather than deleting `/data/adb` state. Use the
bundled `ts18-migrate-magisk-modules.sh`, reboot, then install the combined ZIP.

After reboot, install/update the LSPosed APK and scope it **only to the main
`com.android.systemui` process**. Open **TS18 System UI** from the launcher for
normal configuration and live diagnostics. Root shell helpers remain recovery
and engineering tools, not the normal UX.

## Build and trust

The APK deliberately uses the legacy Xposed bridge qualified for this API29
stack: `assets/xposed_init`, `IXposedHookLoadPackage`, `XposedBridge` and
`xposedminversion=82`. Local stubs are compile-only and CI verifies they are not
packaged. No signing private key or proprietary APK is committed.

Local prerequisites are JDK 17 and Android SDK platform/build-tools 35:

```bash
bash tools/bootstrap-gradle-wrapper.sh
bash tools/test-gradle-wrapper.sh
./gradlew --no-daemon clean test \
  :overlay:lintDebug :visual-overlay:lintDebug :lsposed:lintDebug \
  :overlay:assembleDebug :visual-overlay:assembleDebug :lsposed:assembleDebug
bash tools/test-apk-contract.sh lsposed/build/outputs/apk/debug/lsposed-debug.apk
ALLOW_DEBUG_SIGNING=1 bash tools/package-release.sh debug
bash tools/test-packaged-artifacts.sh debug
```

## Validation status

Repository tests and CI are development evidence, not physical TS18 proof.
Exact-device qualification must still cover right-nav rendering/media dispatch,
Topway brightness confirmation, SystemUI restart, reboot, cold boot, ACC
sleep/wake, reverse camera, calls, projection, keyguard/immersive states and
long-duration stability.

See [`docs/INSTALL.md`](docs/INSTALL.md), [`docs/VALIDATION.md`](docs/VALIDATION.md),
[`docs/RECOVERY.md`](docs/RECOVERY.md) and the remediation roadmap.
