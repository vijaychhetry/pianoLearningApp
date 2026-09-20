# Kids Piano — Android

Native Android app for the kids product spec (`docs/KIDS_PIANO_MASTER_SPEC.md`).
This is **not** the React website.

## What is here

Phase 0 research: `docs/android/PHASE0_RESEARCH.md`  
Phase 1–2: Audio Lab + guided calibration (grown-up tools).  
Phase 3 start: **Level 1 Find this key** (C D E F G) for the child.

| Module | Role |
| --- | --- |
| `:core:notes` | MIDI math, `NoteValidator` (octave-mismatch = soft correct) |
| `:core:pitch` | `PitchDetector` (YIN + HPS), debounce state machine |
| `:core:pitch` (cont.) | `LabSession`, `MicSourceCycler`, `FrameArrivalMonitor` — everything the Audio Lab screen decides, with no Android types so it is testable |
| `:core:calibration` | Profile types, median engine, `concertA4Hz`, and `CalibrationRunner` |
| `:core:learning` | `LessonSession` (Level 1 find-this-key) and the grown-ups math gate |
| `:core:audio` | `AudioRecord` with UNPROCESSED → fallback, drop-oldest queue |
| `:app` | Compose screens; thin adapters over the core session classes |

## Download APK (no Android Studio)

A debug APK is in [`releases/KidsPiano-debug-0.5.6.apk`](../releases/KidsPiano-debug-0.5.6.apk). On GitHub, open the file and click **Download raw file**, then install it on the phone (allow install from that source). Allow **Microphone** when the app asks.

Uninstall an older **Kids Piano** build first if the installer refuses. Do **not** install `KidsPianoLab-debug-0.4.0.apk` — that was a side fork of Audio Lab, not the current product.

## What the child sees

**Home** — **Practice** (C4–G4, one key at a time) once a grown-up has calibrated. **Grown-ups** is a 2-second hold plus a times-table.

**Practice** — pick a course from the left **Courses** drawer (open on Home, hides after you pick or tap outside). Big note name, two-layer keyboard, play that key on the Yamaha. Sequential unlock is off until the app is complete (`REQUIRE_SEQUENTIAL_UNLOCK`). Copy names the octave (`C4`, `E4`). After the five keys, **Next** opens the following course. **Done** goes Home.

## Grown-up tools (unchanged job)

**Setup** — room checklist and sticker colours for C4–G4.

**Calibrate** — five keys, four separate presses each. Key dropdown (A2–F6 whites). A weak profile is not saved as the default.

**Files** — share calibration JSON and the session log. No recordings.

**Audio Lab** — engineering readout (spec §15). Not a lesson.

## Open in Android Studio

1. Install Android Studio (Ladybug / API 35 SDK).
2. **File → Open** the `android/` folder (not the repo root).
3. Let Gradle sync.
4. Plug in a phone or start an emulator, run `app`.
5. Allow microphone. Put the device on a piano (Yamaha PSR-F52 is the spec target). Grown-ups → Calibrate, then Kids Home → Practice.

## Command line (JDK 17+, Android SDK)

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:notes:test :core:pitch:test :core:calibration:test :core:learning:test :core:diagnostics:test
./gradlew :app:installDebug
```

JVM tests need no device (notes/pitch/calibration/learning/diagnostics). They are held to mutation testing rather than to a green tick: see `docs/android/ACCEPTANCE_CRITERIA.md`. Real `AudioRecord` behaviour still needs a phone.

## What we are not building yet

Falling notes, songs, characters, badges, parent dashboard, Play Store listing.
