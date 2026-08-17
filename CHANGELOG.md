# Changelog

## 0.2.0 — 2026-08-17

- Treat the supplied export-renamed sysbar RRO as the exact active TS18 geometry
  artefact rather than a sibling-orientation approximation.
- Record `Android System_10.apk` as the export-renamed
  `/system/framework/framework-res.apk`; keep it distinct from `SystemUI.apk`.
- Make the collapsed shade/input region obey hard product invariants:
  - at least 64 px clear of the physical screen corners (the two top corners are
    the binding constraints for a top-edge region);
  - never wider than 20% of the full screen/status-bar width;
  - exclude the current right-navigation inset;
  - fail open to stock SystemUI if those constraints cannot be satisfied.
- Remove the old `preserve-clickables` escape path because it could reintroduce
  SystemUI touch interception outside the permitted strip.
- For the 1280 px TS18 baseline, change the default collapsed strip from the old
  usable-width calculation to x=960..1216 (right-exclusive), 256 px wide.
- Add pure-Java invariant tests covering the 64 px and 20% safety bounds.
- Add an evidence-gated roadmap for optional Previous / Play-Pause / Next media
  controls in genuinely unused space on the separate right navigation strip.
- Bump source/build/module version to 0.2.0.

## 0.1.0

Initial source scaffold: 43 dp framework geometry RRO plus SystemUI-only
LSPosed touch-region and visual scaling prototype.
