# Level 1 — Find this key

Same Android app as Audio Lab. Not a second project.

## What a child sees

1. **Home** — big **Practice** button. If the piano has not been calibrated (or the saved profile is `NEEDS_IMPROVEMENT`), Practice is disabled and the copy says to ask a grown-up to set up the piano.
2. **Practice** — one letter at a time: **C D E F G** (C4–G4, the same five keys calibration already uses). The child plays that key on the physical piano. Feedback is:
   - Correct → checkmark + “Yes!” then the next letter
   - Wrong letter → “Try C” (stay)
   - Right letter, other octave → “Right letter, try the other C” (stay, not scored wrong)
   - Unclear / two notes / too quiet → “I couldn’t hear that. Try again.” (never scored wrong)
3. After G → “You found all five keys!” and Play again.
4. **Grown-ups** — a small math gate, then the existing Calibrate and Audio Lab screens. Calibration and the lab stay, and keep being improved, as the engine behind Practice.

## What we are not building in this slice

Falling notes, songs, characters, badges, streaks, parent dashboard, Play Store listing.

## Engine

- New JVM module `:core:learning` (`LessonSession`) — no Android types, same pattern as `CalibrationRunner` / `LabSession`.
- A saved `GOOD`/`EXCELLENT` profile is required to start Practice.
- The profile’s five medians become a concert-A4 offset (`concertA4Hz`). That A4 is passed into the pitch detector **and** `NoteValidator`, so a piano that is tens of cents off concert pitch is not marked “unclear” on the right key.
- One physical press is one judgement (debounce to `STABLE`, then wait for release). A held correct C must not be scored as a wrong D after the lesson advances.
- Microphone capture, source fallback, and silence handling stay the same as Calibrate / Audio Lab.

## App shell

`Home` → `Practice` | `Grown-ups gate` → `Calibrate` / `Audio Lab`.

Package id stays `com.vijaychhetry.kidspiano`. Version `0.4.0`. Debug APK goes in `releases/` for GitHub sideload.
