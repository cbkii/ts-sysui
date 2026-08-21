# Build and validation status

## Implemented in source

- exact-device SystemUI contract fixture and installed-APK verifier;
- API29 exact `StatusBarTouchableRegionManager` adapter with asynchronous hash,
  special-state gates and ownership-bounded hidden-listener cleanup;
- 20% full-width maximum, 64px physical corner exclusions and right-nav inset;
- independent framework geometry and exact-SystemUI visual RROs;
- exact Topway `NavigationBarView/navbar_left` weighted media group;
- existing-session `MediaController.TransportControls` client with deterministic
  selection and exactly-one dispatch policy;
- independent compact/nav settings, kill switches and circuit breakers;
- host policies, JUnit, source contracts, Lint/build/package/signature workflows;
- proprietary artifact exclusion and source-manifest validation.

All runtime mutations default off. Release numbering is deferred because the
concurrent brightness-controller pull request already claims 0.5.0.

## Evidence labels

Repository checks establish only **source/static/CI** status. They do not make
the release physically qualified.

The following remain **unverified on the exact TS18** until evidence is
recorded: overlay/idmap acceptance, touch routing, nav measurement/reflow,
media-app behaviour, SystemUI restart, reboot, cold boot, ACC sleep/wake,
reverse camera, calls, projection, keyguard, immersive states and long-duration
stability.

See `VALIDATION.md` for the required progression and
`EXACT-TS18-SYSTEMUI-FINALISATION.md` for the complete implementation record.
