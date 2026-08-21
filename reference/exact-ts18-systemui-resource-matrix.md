# Exact TS18 SystemUI visual resource-use matrix

This is a bounded derived fixture from the supplied exact Android-10 SystemUI
analysis. It records the resources approved for the independently disableable
visual RRO and the resources prohibited because they own or may be shared with
right-navigation geometry.

| Resource | Exact static role | Override | Value | Runtime boundary |
|---|---|---:|---:|---|
| `status_bar_icon_size` | Status icon layout size | Yes | `18dp` | Verify icon clipping and alignment physically. |
| `status_bar_icon_drawing_size` | Status icon drawing size | Yes | `13dp` | Verify OEM icons remain legible. |
| `status_bar_clock_size` | Status clock text size | Yes | `10.5sp` | Verify font-scale/clock baseline physically. |
| `status_bar_height*` | Framework window/inset geometry | No in this RRO | - | Owned only by the geometry RRO. |
| `navigation_key_width` | Topway status/nav shared-risk dimension | Prohibited | - | Never shrink globally. |
| `navigation_bar_width` | Framework right-nav geometry | Prohibited | - | Preserve current OEM strip width. |
| `navigation_bar_height*` | Framework nav geometry | Prohibited | - | Preserve OEM layout. |
| `navigation_bar_frame_height*` | Framework nav frame geometry | Prohibited | - | Preserve OEM layout. |

No OEM layout is copied. If the exact target rejects any allow-listed resource
through overlay/idmap policy, the module must remain disabled and stock visuals
remain authoritative.
