# Physical validation plan

CI/static validation is not physical TS18 proof. Change one variable at a time
and capture before/after state on the exact device.

## Stage A — geometry only

Keep the LSPosed component disabled.

1. cold boot with Magisk geometry enabled;
2. verify the top bar/insets are about 75% of stock and the right nav is unchanged;
3. check normal/fullscreen apps, DoFun home, Settings, notification shade and input surfaces;
4. confirm app content starts at the reduced top inset rather than an invisible
   55 px reservation;
5. repeat after SystemUI/launcher restart, reboot and an ACC sleep/wake boundary.

If visual bar and app inset disagree, **STOP**. Do not compensate with density,
overscan or another blind overlay.

## Stage B0 — LSPosed observation only

Install/scope the LSPosed APK to only `com.android.systemui`, but keep compact
runtime settings disarmed/default. After restart/reboot verify:

- SystemUI remains stable;
- one `TS18StatusBar` hook-install line appears;
- no compact touch/visual behaviour changes while settings are absent/off;
- no repeated circuit-breaker failures occur.

This proves hook attachment only, not runtime correctness.

## Stage B1 — compact input only

Arm input while leaving visual scaling off:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh input-on'
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-off'
```

For the historical 1280 px full-width StatusBar and 55 px right nav inset, the
20% default should be **x=960..1215** (half-open `[960,1216)`). Test app controls
and downward gestures at:

```text
64, 400, 800, 950, 960, 1000, 1100, 1200, 1215, 1216, 1224, 1240
```

Expected collapsed result:

- x < 960: app receives the gesture; shade does not start;
- x = 960..1215: shade can start;
- x >= 1216: this hook does not claim the gesture;
- no claimed point is within 64 px of either physical top corner;
- trigger width never exceeds 256 px at physical width 1280;
- right navigation remains stock;
- expanded shade remains fully touchable.

Also test keyguard and a heads-up notification. They must stay stock. Enable debug
briefly if needed and confirm ordinary collapsed state is classified as
`collapsed-full-width-region`. If debug reports coordinate-space or state-policy
rejection, do **not** weaken the guard to make the test pass; capture current
`dumpsys window`/`dumpsys input` first.

Repeat with a smaller width such as `touch-fraction 0.10`, then `0.01`, to prove
the old 5% floor is gone without weakening the 20% maximum.

If any point outside the strip still opens the shade, capture the exact event
before considering a second gesture authority. No `system_server` hook is
pre-emptively included.

## Stage C — optional visuals

Only after Stage B succeeds, arm visuals:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh visual-on'
```

Inspect clock, signal/battery/notification icons and any Topway leaves. Verify:

- no clipping/overlap/baseline damage;
- an animated/scaled SystemUI leaf is not fought by the module;
- a leaf moving outside the compact bar returns to its original scale when still
  module-owned;
- `visual-off` restores owned transforms after a layout pass/restart;
- circuit-breaker or module disable leaves stock behaviour after SystemUI restart.

If any custom control misbehaves, leave visuals off; geometry/input are independent.

## Stage D — compact lifecycle boundaries

After A/B/C are individually accepted, validate the enabled compact combination
across:

1. SystemUI restart;
2. launcher restart;
3. warm reboot;
4. cold boot;
5. ACC sleep/wake and full power cycle as applicable.

Capture `tools/ts18-statusbar-validate.sh` before/after material failures. Never
promote CI/emulator/static evidence to physical validation.

## Stage N0 — right-navigation observation only

This stage is independent of clickable media controls. v0.4 must not mutate the
right nav.

Before starting:

1. record current Android build identity;
2. record current Magisk/Zygisk/LSPosed/module state and any other SystemUI writer;
3. obtain and retain the current full `com.android.systemui` APK identity;
4. arm only the read-only hierarchy probe.

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-observe'
```

For each material test state, run the bundled bounded collector:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-right-nav-evidence.sh'
```

The collector writes only to shared Download plus a short-lived root scratch
directory. It captures current build/display/package/window/nav settings and
bounded right-nav logs, resolves `pm path com.android.systemui`, copies the
current SystemUI APK(s) into the evidence directory and records SHA-256 values.
Its `EVIDENCE_GATE=PARTIAL` result means binary identity was captured; it does
**not** mean hierarchy/lifecycle qualification is complete.

Expected behaviour while the probe is active:

- navigation bar looks and behaves exactly as before;
- no new clickable/non-clickable view appears;
- no stock bounds move;
- logs contain bounded `right-nav probe generation=...` snapshots;
- root replacement increments generation rather than duplicating observation
  listeners;
- navbar-specific failures do not open the compact `CircuitBreaker`.

Capture each state that is available:

```text
launcher/home
ordinary app
IME visible
immersive/fullscreen
keyguard
reverse camera
phone call
projection
```

Then repeat the observation around:

```text
SystemUI restart
launcher restart
warm reboot
cold boot
ACC sleep/wake
```

For every materially different snapshot record:

- navigation root class and screen bounds;
- stock child class/resource IDs;
- occupied vertical bounds;
- click/long-click semantics exposed by View state;
- layout-param classes;
- candidate genuinely unused intervals;
- reinflation/root-generation behaviour.

Disable the probe after capture:

```sh
su -c 'sh /storage/emulated/0/Download/ts18-statusbar-config.sh nav-probe-off'
```

Stage N0 **does not authorise an inert marker or media button**. Compare the
captures against `RIGHT-NAV-MEDIA-ROADMAP.md`; if the host, free space, stock
semantics or lifecycle remain ambiguous, STOP at observation-only.

## Future right-navigation mutation stages

The later progression is strictly:

```text
N1 inert non-clickable marker
N2 Play/Pause only
N3 Previous/Next + configured order
N4 state-aware presentation
```

Each stage requires the previous stage's evidence and rollback criteria. No
functional nav stage may be inferred from successful CI or Stage N0 alone.
