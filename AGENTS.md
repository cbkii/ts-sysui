# TS18 Status Bar engineering policy

This repository targets CB's exact Topway TS18 Android 10/API 29 unit. Do not
claim generic TS18/TS10 compatibility.

## Authority and safety

- Present exact-device evidence overrides static APK analysis and this repository.
- `com.android.systemui` and framework overlays are protected surfaces.
- Keep changes systemless, reversible, fail-open and independently disableable.
- Do not replace `SystemUI.apk`, delete OEM RROs, write `/system`/`/product`
  directly, or add `system_server` hooks without new exact-device evidence.
- Root does not imply platform signing, UID 1000, signature permissions or
  SELinux authority.
- Scope LSPosed to `com.android.systemui` main process only.
- API-gate anything newer than Android 10/API 29.

## Hard touch-region contract

Any collapsed shade/touch interception implemented by this repository must:

- remain at least 64 px from both physical top corners;
- never exceed 20% of the full status-bar/screen width;
- exclude the current right navigation inset;
- fail open rather than weaken those limits if geometry is impossible;
- never restore clickable/touchable SystemUI regions outside that bounded strip
  merely for convenience.

These are product requirements, not tuneable defaults. Configuration may make
the region smaller or move it farther from a corner, never larger/closer.

## Change discipline

1. Change one layer at a time: geometry RRO first, SystemUI hook second.
2. Preserve the stock right navigation bar in the current release.
3. Any hook failure must leave stock behaviour intact; rollback only state the
   module can still prove it owns.
4. LSPosed first execution must remain observation-only: master/input/visual
   mutations default off and are armed explicitly after hook-load validation.
5. Framework resources are the status-bar geometry authority. Do not reintroduce
   a generic `TYPE_STATUS_BAR` window-height normaliser without exact runtime
   evidence that the RRO is insufficient.
6. Keep the persistent global kill switch and in-process circuit breaker.
7. Validate SystemUI restart, reboot, cold boot and ACC sleep/wake before
   considering a release physically proven.
8. Never claim CI/static success is physical TS18 validation.
9. Future right-nav media controls must be optional, evidence-gated and command
   an existing MediaSession; they must not introduce playback authority or
   displace stock navigation functions.
