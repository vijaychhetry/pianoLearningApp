# Kids Piano — Android (Phase 1 Audio Lab)

Native Android app for the kids product spec (`docs/KIDS_PIANO_MASTER_SPEC.md`).
This is **not** the React website.

## What is here

Phase 0 research: `docs/android/PHASE0_RESEARCH.md`  
Phase 1: **Piano Audio Lab** only — live pitch, no games.

| Module | Role |
| --- | --- |
| `:core:notes` | MIDI math, `NoteValidator` (octave-mismatch = soft correct) |
| `:core:pitch` | `PitchDetector` (YIN + HPS), debounce state machine |
| `:core:pitch` (cont.) | `LabSession`, `MicSourceCycler`, `FrameArrivalMonitor` — everything the Audio Lab screen decides, with no Android types so it is testable |
| `:core:calibration` | Profile types, median engine, and `CalibrationRunner` (the guided flow's frame loop) |
| `:core:audio` | `AudioRecord` with UNPROCESSED → fallback, drop-oldest queue |
| `:app` | Compose screens; thin adapters over the core session classes |

## Download APK (no Android Studio)

A debug APK is in [`releases/KidsPianoLab-debug-0.4.0.apk`](../releases/KidsPianoLab-debug-0.4.0.apk). On GitHub, open the file and click **Download raw file**, then install it on the phone (allow install from that source). Allow **Microphone** when the app asks.

## The two screens

**Calibrate** — this is the setup flow. It asks for one key at a time (C, D, E, F, G) and takes **four separate presses** of each: press, let go, press again. One sample per press, not per frame, because 46 ms apart frames from a single held note measure the detector's own jitter rather than how the piano varies. Wrong keys are rejected and named. Quality is scored Excellent / Good / Needs improvement; a skipped key can never score Excellent, and a Needs-improvement profile is not stored as the default unless the user explicitly keeps it (spec §11).

**Audio Lab** — an engineering readout (spec §15). It does not teach or score. It shows mic level, frequency, MIDI, confidence, compute latency, the applied `AudioSource`, and a scrolling log. The log always writes the first frame and a heartbeat line. A microphone that opens but never delivers a frame is caught by a timer that runs independently of the audio flow, so the screen cannot sit blank waiting for audio that will never come. **Change mic** cycles `UNPROCESSED → VOICE_RECOGNITION → MIC`, because some phones accept UNPROCESSED and return silence; the app also switches automatically, tries each source once, and then says plainly that no source is working.

## Open in Android Studio

1. Install Android Studio (Ladybug / API 35 SDK).
2. **File → Open** the `android/` folder (not the repo root).
3. Let Gradle sync.
4. Plug in a phone or start an emulator, run `app`.
5. Allow microphone. Put the device on a piano (Yamaha PSR-F52 is the spec target). Play **one** key.

## Command line (JDK 17+, Android SDK)

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:notes:test :core:pitch:test :core:calibration:test
./gradlew :app:installDebug
```

113 JVM tests, no device needed. They are held to mutation testing rather than to a green tick: see `docs/android/ACCEPTANCE_CRITERIA.md` for which rule each one protects and what deliberately breaking the implementation proves. Real `AudioRecord` behaviour, on-device latency, and accuracy against an actual piano still need a phone.

## What we are not building yet

Lessons, falling notes, characters, parent dashboard, Play Store listing.
