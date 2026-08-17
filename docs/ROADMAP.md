# Roadmap

## v0.3 baseline — hardened compact status bar

Current scope stays deliberately small:

- framework status-bar height at 43 dp;
- collapsed shade touch strip hard-capped to 20% of screen width;
- mandatory >=64 px top-corner exclusion;
- right-navigation inset excluded;
- observation-only LSPosed first run;
- optional, separately armed 0.75 collapsed visual scaling;
- framework RRO as the sole status-bar height authority;
- no `system_server` hooks;
- no right-navigation mutation.

## Candidate: right-navigation media controls

**Feasibility: plausible, but evidence-gated.** The TS18 exposes a separate
right-side `NavigationBar0`/navigation surface. Injecting additional buttons from
an LSPosed module running in `com.android.systemui` is technically possible in
principle, but it is a protected safety-relevant UI surface and must not be
modified from generic Android assumptions.

### Intended feature

Add a compact optional vertical media group to genuinely unused right-nav space:

```text
Previous
Play / Pause   (state-aware icon if reliable)
Next
```

The controls should target the currently selected/active Android media session.
An optional package preference may be added later, but the first design should
remain generic rather than make Auxio-TS the only supported player.

### Authority model

The buttons are **control clients only**. They must not create another playback
service, MediaSession, queue, notification, audio-focus owner or media database.
Preferred command path:

```text
SystemUI injected button
  -> MediaSessionManager / existing MediaController
  -> active session TransportControls
```

If the exact firmware proves that public Android media control is insufficient,
a Topway-private command path may be investigated separately, but only after its
contract and authority are recovered. Do not copy stock smali or infer MCU/media
commands from unrelated services.

### Evidence gate before implementation

Before adding a functional nav button, establish all of the following from the
exact supplied/current SystemUI artefact and then confirm on-device:

1. exact class/layout owning `NavigationBar0` on this firmware;
2. exact child hierarchy and which portions of the 55 px strip are genuinely
   unused in normal, immersive, keyguard, reverse-camera, call and projection
   states;
3. existing Back/Home/Recents/custom Topway buttons, their click/long-click
   semantics and minimum touch targets;
4. lifecycle/reinflation path so injected buttons are added exactly once;
5. whether SystemUI's process identity can obtain/control the active
   `MediaController` on API 29 without extra privilege mutation;
6. how the right nav changes across launcher restart, SystemUI restart, reboot,
   cold boot and ACC sleep/wake.

If those cannot be established, **STOP** rather than replace or overlay the whole
navigation bar.

### Proposed implementation sequence

1. **Read-only hierarchy/log pass** — identify root and candidate insertion point;
   no visual or touch mutation.
2. **One inert test view** — non-clickable marker in confirmed unused space;
   verify no displacement/relayout of stock controls.
3. **One media button** — Play/Pause only, fail-open, kill-switch protected;
   dispatch to the existing active Android session.
4. **Add Previous/Next** only after exactly-once command behaviour is proven.
5. **State-aware presentation** — update play/pause icon from PlaybackState only
   if callback lifecycle is reliable and bounded.
6. **Optional package targeting** — only if generic active-session selection is
   ambiguous on this unit.

### Safety/rollback requirements

- independent enable switch from compact-status-bar input/visual settings;
- same SystemUI-only scope and in-process circuit breaker;
- never hide/replace an existing stock nav function to make room;
- no blocking package/session scans on SystemUI main thread;
- rate-limit logs and callbacks;
- restore stock layout immediately when disabled/reinflated;
- physical validation across reverse camera, calls, projection, launcher,
  SystemUI restart, reboot, cold boot and ACC boundaries.

This feature should be a later optional component or separately gated module so a
media-control defect cannot regress the already-validated compact status bar.
