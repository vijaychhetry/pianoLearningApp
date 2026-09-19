# Keyboard guide, key picker, and exportable diagnostics

Same Android app. No second project. Level 1 still starts at **C4–G4**. This slice makes those five keys findable on a 61-key Yamaha PSR-F52, lets a grown-up pick which key Calibrate is asking for, and lets us pull the profile and logs off the phone.

## Why the current screens fail a child

The PSR-F52 has 61 keys from **C2 to C7**:

| Letter | How many white keys | MIDI |
| --- | --- | --- |
| C | **6** (C2–C7) | 36, 48, 60, 72, 84, 96 |
| D E F G A B | **5 each** | e.g. D2–D6 |

Practice today shows a giant **C**. That is a letter, not a location. A child can press any of the six C keys. The engine then says “right letter, try the other C” — which is honest, but not teachable.

There is **no key dropdown** on Calibrate. The flow is fixed: C4, then D4, E4, F4, G4. Skip key only advances. If it felt broken, that is because a picker was never built.

## Where the calibration file lives today

Saved in Android `SharedPreferences` named `calibration` (package `com.vijaychhetry.kidspiano`). On the device that is:

`/data/data/com.vijaychhetry.kidspiano/shared_prefs/calibration.xml`

It is **not** a file the parent can open in Files. It is not in Downloads. It is not in the git repo. The on-screen line `Saved profile: EXCELLENT · 5 notes · …` is a summary of that XML.

The compact prefs string also **drops spread Hz**. Export must carry the full in-memory profile when we just finished a run, and persist spread so a later export is still useful.

Session logs today are only the **in-memory** Audio Lab list (about 40 lines). They vanish when the app is killed. Nothing is written to disk.

## Product decisions (this slice)

1. **On-screen 61-key keyboard** (C2–C7, matching PSR-F52) on Practice and Calibrate. Fit all 36 white keys in the phone width so the child can **see all six C labels at once** (`C2`…`C7`). The target key is filled purple with its name (`C4`) on the key. Other C keys stay visible and get a light outline. Only **C** keys are lettered under the strip. Horizontal scroll is a fallback if a very narrow device clips the strip, and then auto-scroll keeps the target on screen. No Yamaha logo. Keys are not tappable in Level 1 — the child plays the real piano.
2. **Level 1 lesson set stays C4–G4** by default. Other letters and other octaves are not dumped on the child in one run. Grown-ups get a **Lesson set** control: `C4–G4 (first)` | `C3–G3 (lower)`. Stored in the same `calibration` prefs as `lessonSet=default|lower`. Home Practice uses that list. Quality for a default profile still requires C4–G4 (spec §11).
3. **Calibrate key dropdown** is real: every white key C2–C7 (36 items), defaulting to the current wizard note. Choosing D4 jumps the wizard to D4 even before Start. Choosing a white key not in the session **appends** it. Skip still works. Extra keys are stored and used for A4 retune if present. Black keys are refused.
4. **Grown-ups gain a third tab: Files** (same top-tab pattern as Calibrate / Audio Lab). No new bottom navigation. Files holds **Lesson set**, **Export calibration**, and **Export session log**. Calibrate also shows a compact Export button after a profile exists.
5. **Export** via the Android share sheet (Drive, Gmail, Files):
   - `calibration-<date>.json` — full profile (quality, source, sample rate, per-note median/spread/count).
   - `session-<date>.jsonl` — one JSON object per line: timestamp, screen (practice/calibrate/lab), expected MIDI, heard MIDI, Hz, status, confidence.
6. README documents how to drop those files into `diagnostics/inbox/` for analysis here.
7. **No raw microphone audio** in exports (spec §12, §38).

## Screens (approved prototype)

Interactive HTML: `docs/ui-mockups/prototype.html`

Static portraits: `docs/ui-mockups/mockup_practice_c4_keyboard.png`, `mockup_calibrate_key_dropdown.png`, `mockup_export_files.png`

### Practice

```
Home
Play this key
        C4          ← hero is note name, not letter alone
       (cue)
   Yes! Now D.
   1 of 5  [====    ]
[ C2 ·· C3 ·· [C4] ·· C5 ·· C6 ·· C7 ]  ← 61-key strip, C4 purple
              Pause
```

Octave-mismatch copy becomes: `That was C5. Press the purple C4.`

### Calibrate

```
Key: [ C4 ▼ ]     ← 36 white keys
Play this key
C4
sample 2 of 4
[ keyboard, same highlight ]
Start   Pause   Skip key
Saved profile: EXCELLENT · 5 notes · …
Export calibration
```

### Grown-ups → Files

```
Grown-ups                         Kids Home
[ Calibrate ] [ Audio Lab ] [ Files ]
Lesson set: [ C4–G4 (first) ▼ ]
Export calibration   → calibration-2026-09-19.json
Export session log   → session-2026-09-19.jsonl
```

## Engine

- New `PianoKeyboard` model in `:core:notes`: PSR-F52 range C2–C7, white/black layout.
- `LessonSession` already takes `notes: List<Int>`. UI passes `expectedMidi` into the keyboard. `lessonSessionFor` gains an optional notes list.
- `CalibrationSession` copies its constructor list into a mutable `noteOrder`. `jumpTo(midi)` moves the index or appends a white key. `notes` becomes a getter.
- New JVM module `:core:diagnostics`: `SessionLog` (in-memory deque, cap 2000) and `calibrationToJson` / `calibrationFromJson`. No Android types. No `sink` callback in the core type — the app writes `toJsonl()` to `filesDir/logs/session.jsonl`.
- Android `FileExporter` uses `FileProvider` + `ACTION_SEND`.
- Prefs `formatSavedNotes` gains a fourth `spread` field so a later export is not missing IQR.

## What we are not building

Falling notes, songs, characters, badges, parent cloud, calibrating all 36 white keys as a required wizard, MIDI cables, a second app, Yamaha branding, a new bottom tab bar, tap-to-play on the on-screen keyboard.
