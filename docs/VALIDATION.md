# Physical validation plan

Treat CI and static analysis as development evidence only. Validate one layer at
a time on the exact TS18.

## Stage A — geometry only

With LSPosed component disabled:

1. cold boot with Magisk geometry enabled;
2. confirm the top bar is about 75% of stock height and right nav is unchanged;
3. check normal apps, fullscreen apps, rotation behaviour if used, DoFun home,
   notification shade, Settings and keyboard/input surfaces;
4. confirm app content begins at the reduced top inset rather than retaining an
   invisible 55 px reservation;
5. restart SystemUI/launcher as applicable and repeat;
6. reboot, then test an ACC sleep/wake boundary.

Any mismatch between visual bar and app inset means **STOP**: do not compensate
with overscan/density or a second blind overlay.

## Stage B — input only

Enable LSPosed with visual scaling temporarily off if you want the smallest
single-variable test:

```sh
su -c 'settings put global ts18_statusbar_visual_enabled 0'
```

For the exact 1280 px historical SystemUI width, the default hard-bounded region
should be **x=960..1216**. It is 256 px wide (20% of screen width), ends 64 px
before the physical top-right corner, and remains left of the historical 55 px
right-navigation strip.

Use an app with known controls along the top edge. Test ACTION_DOWN/tap and short
vertical swipes at physical screen x positions around:

```text
64, 400, 800, 950, 960, 1000, 1100, 1200, 1215, 1216, 1224, 1240
```

Expected collapsed result:

- x < 960: underlying app receives the gesture; shade does not start;
- x = 960..1215: shade can start;
- x >= 1216: the compact-status-bar hook does not claim the gesture;
- the stock right navigation bar remains stock;
- no part of the shade-trigger region is within 64 px of either top corner;
- trigger width never exceeds 256 px on a 1280 px SystemUI window;
- once the shade is expanded, the full shade remains touchable.

Also test a heads-up notification and keyguard. This version deliberately leaves
those transient/locked states stock.

If an x position outside 960..1215 still opens/reveals the notification shade,
capture that exact event before adding any `system_server` hook. A second gesture
path must be proven rather than guessed.

## Stage C — visuals

Enable the default 0.75 visual scale and inspect clock, signal icons, notification
icons and any Topway custom leaves. Verify no clipping, overlap, baseline damage
or animated scale conflict. If any custom control misbehaves:

```sh
su -c 'settings put global ts18_statusbar_visual_enabled 0'
```

The geometry/input solution remains independent.

## Future Stage D — right-navigation media controls

Do **not** combine this with initial status-bar validation. If the roadmap media
controls are implemented later, validate them as a separate variable only after
A-C pass. Existing navigation buttons must retain their exact functions, hit
regions, long-press behaviour and lifecycle handling before media controls are
considered acceptable.

## Acceptance boundaries

Repeat immediate test, SystemUI restart, launcher restart, normal reboot, cold
boot and ACC sleep/wake. Record failures separately. Do not claim physical pass
for an unrun lifecycle boundary.
