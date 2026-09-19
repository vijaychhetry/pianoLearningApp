# UI prototypes (pre-implementation)

These screens are the design to implement. They are **not** screenshots of a built APK.

## 0.6.0 — level ladder, songs, falling game

- `kids-prototype.html` — all five kid screens side by side. Open this one first.
- `kids_levelmap.png` — eight stops, stars, locked stops dimmed.
- `kids_drill.png` — levels 1–6. Hero is the note name in the key's colour; the key the child actually hit is ringed amber.
- `kids_song.png` — note bubbles with lyric syllables, rhythm-free.
- `kids_falling.png` — tiles falling to the hit line above the keyboard. Two tiles, not three: a 3 s fall with a 2.5 s gap means only two can share the screen. The lower tile is past the line and fading, because over a third of the hit window lives there.
- `kids_summary.png` — stars and the song offer.

Shared assets: `kids.css`, `kids-keyboard.js`, `shot-levelmap|drill|song|falling|summary.html`.

Key colours are fixed and used everywhere: C red `#E5484D`, D orange `#F76B15`,
E yellow `#E8B931`, F green `#2FA84F`, G blue `#3A7DDE`. Colour is never the only
signal — the note name is always shown too.

## 0.5.0 — keyboard guide and export

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
