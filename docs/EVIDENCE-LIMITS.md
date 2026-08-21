# Evidence limits and unresolved items

## Established exact/static evidence

- The target is Topway `s9863a1h10`, Android 10/API 29; last-observed base
  display is 1280x720 at density override 153 with about 55px top/right regions.
- Exact installed-name SystemUI is `/system/priv-app/SystemUI/SystemUI.apk`,
  package `com.android.systemui`, version/min/target SDK 29, shared UID
  `android.uid.systemui`, platform-signed, and declares
  `android.permission.MEDIA_CONTENT_CONTROL`.
- The supplied exact SystemUI APK SHA-256 is
  `668dec9ac14fbabd76ae73d693dcdd1518190f7941b6ac0b00d16587d6c4bd3f`.
- Its Android-Q touch authority has the required
  `StatusBarTouchableRegionManager` fields/methods used by the exact adapter.
- Its Topway `navigation_bar.xml` has vertical weighted `navbar_left` with the
  seven known stock functions recorded in the contract fixture.
- The exact SystemUI resource-use review allows only the three status visual
  dimensions in `reference/exact-ts18-systemui-resource-matrix.md`.
- AOSP Android 10 source is a secondary cross-check for the stock touch-manager
  semantics; analogous UIS7862/FYT APKs are context only.

The repository retains derived contract data and hashes, not proprietary APK
binaries or decoded firmware.

## Enforced rather than assumed

At runtime, exact behavioural adapters still verify:

- API/device/package APK hash;
- reflection members before hook installation;
- full-width physical coordinate mapping and ordinary stock touch region;
- absence of special shade/keyguard/HUN/bubble/layout states;
- exact direct seven-child nav topology;
- uniform positive OEM weights and no unknown child; and
- a measured projected nav cell of at least 56dp.

A mismatch leaves stock behaviour. Historical 1280x720/55px measurements are
never used as hardcoded permission to mutate.

## Proven by repository validation

Host policies and Android CI can prove source invariants, compilation, unit
tests, Lint, allow-lists, packaging, signature checks and absence of prohibited
tracked artifacts. They can prove that source contains no playback authority,
media-key fallback, guessed Topway command or broad `system_server` hook.

They cannot prove what an exact physical unit rendered, touched or dispatched.

## Still requires exact-device evidence

1. Current framework and SystemUI RRO/idmap acceptance, overlay precedence and
   post-reboot persistence.
2. Exact touch delivery to apps outside the strip and shade delivery inside it
   across portrait/landscape if supported, keyguard, HUN, bubbles and expanded
   states.
3. Whether an additional OEM/system-server transient-bar gesture competes with
   the SystemUI window on this firmware. No framework-process hook is included.
4. Current Magisk/Zygisk/LSPosed versions, module scope and interaction with
   other SystemUI writers.
5. Measured right-nav child sizes, padding, press feedback, reinflation and
   enable/disable restoration on the unit.
6. Active-session selection and exactly-once Previous/Play/Pause/Next behaviour
   for each intended media app, including no-session and unsupported-action
   states.
7. Reverse camera, call, projection/CarPlay/Android Auto, immersive, keyguard and
   power-off/screen-off behaviour.
8. SystemUI restart, reboot, cold boot and ACC sleep/wake reliability and logs.
9. Long-duration stability and absence of SystemUI crash loops or input lockout.

Android 16 is not a target. These gaps do not justify partition writes,
SystemUI replacement/resigning, global density/overscan changes, nav-dimension
overrides, guessed vendor commands or a `system_server` hook.
