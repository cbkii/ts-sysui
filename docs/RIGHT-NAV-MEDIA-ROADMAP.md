# Exact TS18 right-navigation media controls

## Product outcome

Optionally add Previous, Play/Pause and Next to the exact TS18 right-side
SystemUI navigation strip while preserving every OEM function. The feature is
independently recoverable and remains a client of existing media authority only.

The first installed implementation showed no custom controls. Exact supplied
`SystemUI.apk` analysis identified the immediate cause: the lifecycle/host were
correct, but the controller's required child resource names were not.

## Exact controlling evidence

The exact API29 SystemUI APK establishes:

- `com.android.systemui.statusbar.phone.NavigationBarView.onFinishInflate`;
- vertical weighted `com.android.systemui:id/navbar_left`;
- exact direct-child resource names:

```text
required physical controls:
navbar_home
navbar_back
navbar_history
navbar_volume_plus
navbar_volume_reduce

known optional decoded controls:
navbar_guanping
navbar_app
```

The superseded generic resource names `home`, `back`, `recent_apps` and `app`
must not be accepted as aliases for this exact binary. Their use caused the
previous exact preflight to reject the real host before media injection.

Current physical observation confirms Home, Recents, Back, Volume+ and Volume−
are visible on the unit. Screen/power and app-slot remain known exact-static
children that may be conditional or `GONE`.

## Runtime host contract

The exact lifecycle hook accepts only supported `NavigationBarView` from the
exact SystemUI binary. Before mutation, preflight requires:

- exact supported APK identity;
- stock navigation root attached, visible and shown;
- direct vertical `navbar_left` visible and shown;
- all five exact mandatory `navbar_*` children present directly;
- any present optional `navbar_guanping`/`navbar_app` direct and unchanged;
- no unknown or duplicate direct child;
- each recognised stock child retaining weighted `height=0` layout;
- uniform positive visible stock weights;
- no explicit host weight sum;
- non-zero host geometry; and
- production-safe sizing.

Diagnostics must capture the live direct-child resource entry name, index and
visibility before returning a topology STOP. Unknown children remain a STOP;
do not weaken matching merely to make injection appear.

One owner-tagged vertical group is inserted before the two stock volume buttons.
Its outer weight equals `stockUnitWeight * actionCount`; each media child has
weight 1. No OEM `LayoutParams`, ID, listener or View instance is edited.
Removal therefore restores stock simply by removing the owned group.

## Stock visibility and vehicle-state lifecycle

Module media controls are subordinate to stock Topway panel visibility. A public
View lifecycle monitor observes exact `NavigationBarView`/`navbar_left`; it never
hooks MCU/reverse commands and never calls `setVisibility()` on stock UI.

When the stock root/host is detached, hidden or not shown, remove the owned group
and stop its MediaController observer. When stock returns, repeat the complete
identity/topology/measurement preflight.

## Physical sizing policy

- vertical production target: **>=56dp**;
- horizontal hard floor: **>=48dp**;
- horizontal preferred target: **>=56dp**;
- use the entire existing OEM host width;
- never widen/narrow the stock strip merely to satisfy dp rounding;
- never scroll or overlap controls;
- insufficient projected vertical height is a hard STOP.

## Activation and observability

The TS18 System UI dashboard sends a coherent signature-protected request to the
injected SystemUI bridge. Applying nav configuration invalidates the config cache
and immediately requests reconciliation; armed-only polling remains a resilience
fallback.

The dashboard/Diagnostic Console exposes:

```text
nav hook installed
root seen/attached
navbar_left host seen
preflight reason
host width/height/density
live direct-child resource names
recognised stock child summary
projected cell size
horizontal floor/preferred state
injected actions
nav breaker state/failure count
media controller count/selected package/state/action bits
```

Fail-open without a visible reason is not acceptable product behaviour.

## Media authority

`NavMediaSessionRepository` observes active sessions on its own HandlerThread,
keeps/selects one deterministic usable existing controller, publishes state on
main and maps each accepted click to at most one `TransportControls` command.

It never creates a MediaSession, service, queue, notification or audio focus and
never combines TransportControls with a media-key/vendor fallback.

Buttons use module-owned vectors, generated runtime IDs and an ownership tag.
No usable session/capability leaves configured controls visible but disabled,
which distinguishes host injection from media availability.

## Physical progression

After installing the corrected build:

1. inspect identity, nav hook/root/host and **live direct-child names**;
2. confirm the names match the exact contract above;
3. enable Play/Pause only with no media session and require one visible disabled
   module cell;
4. verify all OEM controls remain unchanged;
5. start a media session and require exactly one play/pause operation per tap;
6. test paused/no-session/destroyed/multiple-session conditions;
7. add Previous/Next only after Play/Pause passes;
8. recheck dimensions and OEM functions;
9. disable/re-enable/reinflate and verify exactly one owned group; and
10. exercise stock panel hide/show, reverse/fullscreen, SystemUI restart, reboot,
    cold boot and ACC sleep/wake.

## STOP conditions

Remain stock if identity/reflection/resources differ, an expected exact resource
is missing, an unknown/duplicate direct child appears, geometry is unsafe, stock
nav is hidden/not shown, OEM behaviour changes, media selection is ambiguous, a
tap duplicates, or owned-group cleanup fails.

Do not broaden topology, reintroduce generic AOSP resource aliases, force stock
navigation visible, hide stock controls, reduce vertical targets, widen LSPosed
scope or introduce a second playback authority to bypass a STOP. Source/CI
completion is not physical acceptance.
