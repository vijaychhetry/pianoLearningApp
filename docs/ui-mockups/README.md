# UI prototypes for 0.5.0 (pre-implementation)

These screens are the design to implement. They are **not** screenshots of a built APK.

- `prototype.html` — interactive three-phone layout. Open it in a browser. The Calibrate **Key** dropdown changes the highlighted key. This HTML is the source of truth for copy and layout.
- `shot-*.html` — single-phone pages used to capture the PNG mockups.
- `mockup_practice_c4_keyboard.png` — Practice: hero is **C4**, 61-key strip, target filled purple.
- `mockup_calibrate_key_dropdown.png` — Calibrate: working Key dropdown + same keyboard + export.
- `mockup_export_files.png` — Grown-ups **Files** tab (third top tab, not a new bottom bar).

The keyboard is two layers: a 61-key mini-map (all six C labels) and a zoomed
two-octave strip so the target is large enough to read. Calibrate’s dropdown
lists A2–F6 only.

Rules the pictures must keep:

- No Yamaha logo.
- No Play / Practice / More bottom navigation.
- Only C keys are labeled under the strip (`C2`…`C7`).
- Child Practice still teaches five keys at a time (default C4–G4).
