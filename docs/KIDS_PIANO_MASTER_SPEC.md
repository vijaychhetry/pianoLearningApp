# Kids Piano Learning App — Master Specification (v3.0)

> **This is now the single canonical spec.** It merges and replaces both the prior `v2.0 (PDF)` and the earlier `.md` version, which had drifted apart (the PDF added an engineering checklist the .md never received; the .md referenced Essentia as a future consideration that the PDF dropped). Retire both source files once this is adopted.

---

## Changelog — v3.0 (Review Additions)

New material added in this revision, based on architecture review. These are marked inline with **`[NEW v3.0]`** in their respective sections so they're easy to find:

1. **Foreground service requirement** for background/lock-screen microphone capture (Android 12+) — Section 3, Section 38.
2. **Latency target reframed per-note** — low fundamentals (C4 etc.) need more buffered audio than high notes; a single flat `<100ms` target isn't physically achievable across the whole range. Section 16.
3. **Explicit backpressure policy** for the PCM Flow/Channel pipeline — fixed-capacity ring buffer, drop-oldest, not unbounded. Section 3.
4. **Accessibility: don't rely on red/green alone** for correct/incorrect feedback. Section 18, Section 22.
5. **Octave-matching policy** must be decided before `NoteValidator` is implemented — is a note one octave off a match? Section 44.
6. **Play Store "Designed for Families" / kids-app compliance** section added — this was previously absent. New Section 39a.
7. **Split test strategy explicitly into desktop JUnit5 tests vs. on-device Android instrumented tests** — `AudioRecord` and real sample-rate/latency behavior can't be validated on the JVM alone. Section 41.

---

## 1. Product Vision

Build a kid-friendly Android piano-learning app that allows a child to learn independently using a real physical piano/keyboard.

The child places an Android phone/tablet near the piano. The app listens through the device microphone, recognizes the note being played, and immediately tells the child whether the correct key was played.

No parent or teacher should be required to verify basic practice.

**Core principle: audio accuracy first, games second.**

---

## 2. Platform Decision

### Initial platform
- Android
- Kotlin
- Jetpack Compose
- Native Android audio APIs

### Why native Android?
Real-time microphone processing and low-latency audio are core functionality. Native Android gives better control over:
- `AudioRecord`
- audio buffer configuration
- sample rate
- audio latency
- microphone permissions
- foreground/background audio behavior
- device-specific audio characteristics

React Native can be considered later for non-audio portions or a future cross-platform version, but the first production implementation should be native Android.

---

## 3. Recommended Technology Stack

### UI
- Kotlin
- Jetpack Compose
- Material 3
- Compose Animation
- Navigation Compose
- Low-level frame synchronization (e.g., `produceState` / `withFrameNanos`) for smooth rendering in game modes

### Audio Input & Threading
- Android `AudioRecord` using `MediaRecorder.AudioSource.UNPROCESSED` (fallback to `VOICE_RECOGNITION` with hardware effects explicitly disabled) to prevent auto-gain control (AGC) distortion.
- Not all devices honor `UNPROCESSED` — check `AudioRecord` initialization result and effectively-applied source at runtime, and fall back gracefully rather than assuming success.
- Dynamic sample rate querying via `AudioManager.getProperty(PROPERTY_OUTPUT_SAMPLE_RATE)` rather than hardcoding 44.1kHz or 48kHz.
- Dedicated background worker/dispatcher running at `Process.THREAD_PRIORITY_AUDIO` priority.
- Kotlin Coroutines Channel/Flow pipelines to pass PCM byte arrays from the audio thread to the processing pipeline.
- **`[NEW v3.0]` Explicit backpressure policy:** use a fixed-capacity ring buffer / bounded `Channel` with a **drop-oldest** overflow policy, not an unbounded queue. Under system stress (GC pause, thermal throttling), an unbounded buffer silently grows and *increases* end-to-end latency across a session rather than surfacing the problem. For a live pitch-tracking UI, a dropped stale frame is preferable to accumulating a backlog — "never drop a frame" is the wrong goal here.
- **`[NEW v3.0]` Foreground service:** if practice sessions need to survive the screen turning off or the app backgrounding, declare a foreground service with `FOREGROUND_SERVICE_TYPE_MICROPHONE` (required from Android 12 / API 31 onward) and account for the system's mic-in-use indicator in the UI. Without this, continuous background capture will be killed by the OS on real devices. See also Section 38.

### Pitch Detection
Primary open-source reference:
- **TarsosDSP**

Evaluate:
- YIN
- MPM / McLeod-style pitch detection
- Other detectors available through the library if testing demonstrates better piano performance

Do not blindly trust a single detector. Build an abstraction: `PitchDetector`, so implementations can be swapped and benchmarked.

### Local Storage
- Room for structured application data
- DataStore for lightweight settings
- No server should be required for core note recognition

### Architecture
- Clean Architecture / feature-oriented modular architecture
- MVVM or MVI for UI state
- Coroutines + Flow
- Dependency injection using Hilt

---

## 4. Open-Source References & Tooling Additions

The implementation team/AI coding agent MUST review these references before implementing the audio engine.

### TarsosDSP
Primary reference for real-time audio and pitch detection.

Repository: https://github.com/JorenSix/TarsosDSP

Use it to understand:
- pitch detection
- audio processing pipelines
- audio buffers
- YIN
- real-time processing

Wrap the library behind custom interfaces — do not copy its architecture blindly, so the implementation can be replaced or upgraded.

### Aubio
Reference for pitch tracking concepts and tuner-style audio processing.

Repository: https://github.com/aubio/aubio
Android tuner/reference project: https://github.com/marcosfad/Android-aubio-tuner

Study:
- pitch tracking
- confidence
- noisy-input handling
- tuner UX
- real-time processing

### Essentia (future reference only)
Potential future reference for advanced audio analysis: https://github.com/MTG/essentia

Do **NOT** add Essentia to the MVP unless testing shows a real requirement.

### Testing & QA Tooling
- JUnit 5 + MockK + Parameterized Tests for offline audio regression tests against recorded `.wav` samples.
- See Section 41 for the split between these desktop-run tests and on-device instrumented tests.

---

## 5. Audio Recognition Is the Most Important Component

The app must never confidently tell a child that they played the wrong note when the microphone signal is unclear.

Recognition states:

1. `NO_SIGNAL`
2. `LISTENING`
3. `NOTE_DETECTED`
4. `HIGH_CONFIDENCE`
5. `LOW_CONFIDENCE`
6. `AMBIGUOUS`
7. `CORRECT`
8. `INCORRECT`

Example:

Expected: C

- C, high confidence → `CORRECT`
- D, high confidence → `INCORRECT`
- C#/C ambiguous → `LOW_CONFIDENCE` / retry
- background noise → `NO_SIGNAL`
- multiple simultaneous notes → `AMBIGUOUS`

**Polyphony Handling:** The MVP targets a strictly monophonic architecture. If multiple fundamental frequencies or heavy harmonics cross the detection threshold simultaneously (overlapping notes or complex multi-key presses), the system must flag the state as `AMBIGUOUS` rather than guessing incorrectly.

Never convert uncertainty into a wrong answer.

---

## 6. Audio Processing Pipeline

Microphone (`AudioSource.UNPROCESSED`) → AudioRecord Thread (Priority Audio) → PCM audio buffer → preprocessing → noise/silence detection → windowing → pitch detector → frequency → note conversion → confidence estimation → calibration correction → expected-note comparison → game/learning engine → visual/audio feedback

Each stage should be independently testable.

---

## 7. Frequency to Musical Note

For a detected frequency `f`, calculate the nearest MIDI note using:

`midi = 69 + 12 * log2(f / 440)`

Then convert MIDI to note name, octave, and pitch class.

Examples: A4 = 440 Hz, C4 ≈ 261.63 Hz, C#4 ≈ 277.18 Hz, D4 ≈ 293.66 Hz.

The app should internally use MIDI note numbers rather than relying only on note names.

---

## 8. Calibration — Critical Feature

Calibration adapts recognition to different keyboards (such as the Yamaha PSR-F52 baseline model), microphone distance, room acoustics, piano volume, and device microphone characteristics. It must establish a reliable statistical recognition profile rather than a single static frequency, and should be available during first setup and later from Settings.

---

## 9. Calibration Flow

**Step 1 — Setup guidelines:** keep the phone in its normal playing position, keep the piano at normal playing volume, minimize background noise.

**Step 2 — Collect known notes sequentially,** e.g. C, D, E, F, G for the initial 5-key setup. Capture several clean samples per note. For broader calibration, collect more notes across the keyboard.

---

## 10. Calibration Sample Collection

For every calibration note:

1. Wait for silence.
2. Ask the child to play the note once.
3. Detect note onset.
4. Capture multiple analysis frames.
5. Reject noisy/ambiguous frames.
6. Calculate: detected frequency, median frequency, frequency spread, confidence, signal strength, stability.
7. Save a statistical profile — do **not** save only one raw frequency.

Example conceptual profile:

```text
C4
expectedFrequency: 261.63
observedMedian: 260.9
frequencySpread: 1.8 Hz
confidence: 0.96
sampleCount: 12
```

---

## 11. Calibration Validation & Quality Score

After calibration, perform a blind test — ask the child to play C → D → E → F → G without displaying the expected answer in advance.

If recognition is unreliable: *"Let's try that again. I couldn't hear the piano clearly."*

Assign a quality score: **Excellent / Good / Needs Improvement.** A poor profile must not silently become the production default.

---

## 12. Calibration Storage

Store locally via Room/DataStore.

```text
CalibrationProfile
- id
- deviceIdHash (non-identifying local identifier if needed)
- createdAt / updatedAt
- sampleRate
- microphoneSource
- pianoType / pianoName (optional)
- microphoneDistance (optional)
- notes[]
- calibrationQuality
```

```text
NoteCalibration
- midiNote
- expectedFrequency
- observedMedianFrequency
- frequencySpread
- confidence
- sampleCount
```

Avoid storing raw microphone recordings by default. If diagnostic recordings are ever introduced, they must be explicitly opt-in and clearly communicated to parents.

---

## 13. Calibration Recheck

Provide a manual trigger: **Settings → Audio & Piano → Recalibrate.**

Also detect environmental drift: if the app sees repeated low-confidence states, prompt *"Your piano sounds different today. Would you like to recalibrate?"* Do not force calibration unnecessarily.

---

## 14. Piano Sound Challenges & Target Hardware

A real piano produces fundamentals, harmonics, resonance, sustain, and room reflections. The system must **not** rely on simple amplitude threshold + FFT peak alone.

Initial benchmarks and tests target entry-level digital keyboards such as the **Yamaha PSR-F52** (built-in speakers, balanced output timbre, no USB-MIDI dependency for microphone apps).

Test the detector against: acoustic piano, digital piano, electronic keyboard, different microphone distances, quiet room, moderate room noise, child speaking while playing, sustained notes, short notes, loud notes, soft notes.

---

## 15. Audio Lab — MUST BE BUILT FIRST

Before building game UI, build an internal engineering tool ("Piano Audio Lab") displaying live: microphone level, detected frequency, MIDI note, note name/octave, confidence, pitch stability, signal-to-noise estimate, detector status, calibration offset/profile, and processing latency — plus a scrolling event log.

```text
Detected: C4
Frequency: 261.2 Hz
Confidence: 96%
Stability: HIGH
Signal: GOOD
Latency: 55 ms
```

```text
16:31:04 C4 0.96 CORRECT
16:31:05 D4 0.94 CORRECT
16:31:06 D#4 0.52 AMBIGUOUS
```

This tool is for engineering/testing and can be hidden from normal children.

---

## 16. Recognition Latency Target

- Audio capture + processing: target **<100 ms**
- User-perceived response: target **<150 ms**

Benchmark real devices rather than assuming a fixed latency. Avoid excessive debounce that makes the piano feel unresponsive.

**`[NEW v3.0]` Reframe as a per-note target, not a flat ceiling.** Pitch detection on low fundamentals (e.g. C4 ≈ 261Hz) inherently needs several full wave cycles of buffered audio before a stable YIN/autocorrelation estimate is possible — realistically 40–90ms of audio alone before processing overhead, before any code runs. A single flat `<100ms` target across the whole range isn't physically achievable for the lower notes. Instead:
- Report latency per-note in the Audio Lab, not as a single aggregate number.
- Set the target relative to each note's minimum required window length (e.g. "processing overhead beyond the theoretical minimum window should stay under 30–40ms").
- Don't let engineers chase an unachievable flat number for low notes — that's a physics constraint, not an implementation bug.

---

## 17. Note Debouncing

A single piano press creates many audio frames; avoid interpreting `C C C C C C C` as six separate presses.

Use onset detection, note stability, release detection, minimum note interval, and a state machine:

`IDLE → ATTACK → STABLE → RELEASE → IDLE`

---

## 18. Wrong Key Feedback

If expected note = A:

- Child plays A → green feedback, success animation, positive sound, character celebration.
- Child plays B → red feedback, *"Try A!"*, highlight A key.
- Detected note uncertain → **do not show red.** Neutral feedback: *"I couldn't hear that clearly. Try again."* Never mark uncertainty as wrong.

**`[NEW v3.0]` Don't rely on red/green alone.** This app is built specifically for young children, among whom undiagnosed color vision deficiency is common, and red/green is the classic problem pair. Pair every color cue with a distinct shape/icon and animation (e.g. a checkmark burst for correct, a gentle shake/outline for incorrect, a pulsing question-mark for unclear) so the state is legible without relying on color alone.

---

## 19. Learning Progression — Level 1: Recognize Keys

Teach individual notes one at a time. *"Find A"* → show A on screen → child presses A on the physical piano → microphone verifies it. Then B, C, D, E.

---

## 20. Level 2 — Repeated Notes

Examples: `A A A`, `A A B`, `C C D`. The child learns to recognize and play notes repeatedly.

---

## 21. Level 3 — Short Sequences

Examples: `A B C`, `C B A`, `A C B`. The app verifies correct notes, correct order, and basic timing.

---

## 22. Level 4 — Falling Notes

Notes appear as falling blocks (e.g. `A → A → B → C → D`) that fall toward the corresponding piano key. The child plays the physical key when the block reaches the target area.

- Wrong note: block turns red, key feedback shows expected note, score reduced slightly.
- Correct note: block disappears, sparkle/character animation, score increases.

Applies the same color-plus-shape accessibility note as Section 18 — the red/green distinction here should also carry a shape or icon cue.

---

## 23. Note Duration

Block length represents duration — short block = short press, long block = hold the key. The microphone engine should detect onset, sustained pitch, and release. Do not judge duration too aggressively for beginners.

---

## 24. Rhythm Game

Teach rhythm separately from pitch, using visual timing guides. Examples: `C — C — C`, then `C C — C`, then `C ——— D — E`.

---

## 25. Echo Game

The app plays a short sequence (e.g. `C D E`); the child repeats it and the microphone validates it. Difficulty increases: 2 notes → 3 → 4 → 5 → longer phrases.

---

## 26. Memory Game

Show `C D E`, hide them, and the child must remember and play them back. Reward with stars, coins, character reactions.

---

## 27. Song Mode

Use simplified beginner arrangements — public-domain/basic songs (Twinkle Twinkle Little Star, Ode to Joy, Mary Had a Little Lamb, simple original melodies). Avoid copyrighted arrangements unless properly licensed. Divide songs into small sections: Part 1 → Part 2 → Part 3 → Full song.

---

## 28. Performance Mode

Child plays an entire song; evaluate note accuracy, timing, rhythm, completion, and consistency. Keep feedback encouraging:

```text
Great job!
Notes: 94%
Timing: 88%
Rhythm: 91%
3 Stars
```

---

## 29. Free Play Mode

Important: the child should be able to play freely without being judged. Show optional visual reactions and encourage experimentation. Never make every interaction a test.

---

## 30. Daily Motivation

Add a daily mission, e.g.:

```text
Today's Piano Mission
✓ Learn 2 new notes
✓ Play 10 correct notes
✓ Complete 1 rhythm challenge
Reward: ⭐ 20 stars
```

---

## 31. Streaks

Track daily practice streak, weekly practice, total sessions, total notes played, songs completed. Avoid punishing children for missing a day — use *"Your piano journey continues!"* rather than *"You lost your streak!"*

---

## 32. Badges

Examples: First Note, Five Notes Learned, Perfect 10, First Song, 3-Day Explorer, 7-Day Piano Star, Rhythm Master, Note Detective, Song Performer, Piano Explorer. Badges should represent meaningful progress.

---

## 33. Characters

Use original characters inspired by broad themes (unicorn, cute animals, magical princess/fashion character, friendly puppy, space explorer).

**IMPORTANT:** Do not copy or use copyrighted characters (e.g. Barbie, Peppa Pig) without licensing. Create original characters with their own names, appearance, personalities, and animations.

---

## 34. Character Progression

The child's character grows with them — unlock outfits, accessories, backgrounds, instruments, animations, rooms, stickers. Rewards should primarily come from actual musical progress.

---

## 35. Child Profile

Profile contains: nickname, avatar, current level, learned notes, songs, badges, stars, practice streak, achievements. Avoid collecting unnecessary personal information.

---

## 36. Parent Dashboard

Core learning should not require parent involvement. Optional parent area can show notes learned, practice time, accuracy, songs completed, current level, weekly progress, calibration status. The child should not need a parent to start practicing.

---

## 37. Offline-First Requirement

All core functionality — microphone recognition, calibration, learning levels, games, songs, progress, rewards — operates fully offline. Internet should only be required for optional future functionality: content updates, cloud backup, new songs, analytics, parental sync.

---

## 38. Privacy

Microphone access is requested only during active practice — do not continuously record. Prefer: microphone → process locally → discard raw audio instantly. Do not upload microphone recordings for normal operation. Clearly explain microphone permission to parents/users.

**`[NEW v3.0]` Foreground service & mic indicator:** if any practice mode is expected to keep listening while the screen is off or the app is backgrounded, this requires a declared foreground service with `FOREGROUND_SERVICE_TYPE_MICROPHONE` (Android 12+) — see Section 3. Design the UI to acknowledge the system's persistent mic-in-use indicator rather than surprising the parent with it.

---

## 39. Testing Strategy

Audio recognition needs significantly more testing than ordinary UI features.

- **Devices:** low-end, mid-range, and flagship Android; different Android versions.
- **Instruments:** acoustic piano, digital piano, electronic keyboard — with the Yamaha PSR-F52 as the primary reference.
- **Environments:** quiet room, fan hum/noise, moderate background noise, variable microphone distances.
- **Playing styles:** soft, normal, loud, short, sustained.

---

## 39a. Kids-App Store Compliance `[NEW v3.0]`

This app targets children and will presumably be distributed via Google Play. The existing privacy section (local-only processing, no raw audio upload) is necessary but **not sufficient** for approval as a children's app. Before submission, confirm:

- Enrollment in Google Play's **Designed for Families** program requirements (or equivalent for other stores).
- No behavioral/interest-based advertising directed at children; any ads (if added later) must use contextual, non-personalized ad networks certified for child-directed content.
- Accurate completion of the Play Console **Data Safety** section reflecting exactly what is (and isn't) collected — should be minimal, given the local-only design.
- No third-party SDKs (analytics, crash reporting, etc.) that collect persistent identifiers from children without the appropriate consent flow — audit any SDK added during development against this before merging it.
- Parental gate before any external links, purchases, or parent-dashboard access, if those are added in later phases.

This should be revisited at each phase, not just before launch, since adding a single ill-suited SDK during a later phase (e.g. a crash reporter) can retroactively break compliance.

---

## 40. Recognition Metrics

Track during development: note accuracy, false positive rate, false negative rate, ambiguous detection rate, latency, calibration success rate. Target numbers must be established through real-world testing rather than invented upfront. Create a test dataset from controlled piano recordings.

---

## 41. Automated Audio Tests

**`[NEW v3.0]` Explicitly split into two tiers — desktop tests are not sufficient proof of on-device behavior:**

**Tier 1 — Desktop/JVM (JUnit 5 + MockK):**
- frequency → MIDI conversion
- note boundary calculation
- calibration calculations
- confidence calculations
- debounce logic (as a pure state machine, fed pre-recorded frame sequences)
- expected-note matching

**Tier 2 — On-device Android Instrumented Tests (`androidTest`):**
- actual `AudioRecord` initialization and effectively-applied audio source per device
- real device sample-rate behavior vs. requested rate
- measured end-to-end capture + processing latency on physical hardware
- onset/release detection against live or injected real audio, not just synthetic waveforms

Every detector/library change should be benchmarked against the recorded-audio regression dataset in Tier 1, **and** spot-checked on at least one low-end and one flagship physical device before merging, since desktop JVM behavior does not reliably predict on-device `AudioRecord` behavior.

---

## 42. Architecture

```text
app
core
  audio
  pitch
  calibration
  notes
  timing
  storage
  common

feature
  onboarding
  learning
  fallingnotes
  rhythm
  echo
  songs
  freeplay
  rewards
  profile
  settings
  parent

data
  local
  repository

ui
  theme
  components
```

---

## 43. Important Interfaces

```kotlin
interface AudioInput {
    fun start()
    fun stop()
    fun audioFrames(): Flow<AudioFrame>
}

interface PitchDetector {
    fun detect(frame: AudioFrame): PitchResult
}

interface CalibrationEngine {
    suspend fun calibrate(samples: List<AudioSample>): CalibrationProfile
}

interface NoteRecognizer {
    fun recognize(pitch: PitchResult): RecognizedNote?
}

interface NoteValidator {
    fun validate(
        expected: MidiNote,
        detected: RecognizedNote
    ): ValidationResult
}
```

---

## 44. Recognition Result

```text
PitchResult
- frequency
- midiNote
- confidence
- clarity
- signalStrength
- stability
- timestamp
```

```text
ValidationResult
- status
- expectedNote
- detectedNote
- confidence
- timingOffset
```

Status: `CORRECT`, `INCORRECT`, `UNCLEAR`, `NO_SIGNAL`, `AMBIGUOUS`

**`[NEW v3.0]` Octave-matching policy must be decided before `NoteValidator` is implemented.** If the expected note is C4 and the child plays C5 (right pitch class, wrong octave — common on small keyboards or when a child is exploring), is that:
- `CORRECT` (pitch-class match only), or
- a distinct `CORRECT_OCTAVE_MISMATCH` / soft-correct status with its own feedback ("Right note, try the other C!"), or
- `INCORRECT`?

This is a product decision, not just an engineering detail — pick one explicitly and encode it in `ValidationResult`/`NoteValidator` rather than leaving it to be discovered ad hoc during testing.

---

## 45. Development Phases

**Phase 0 — Research:** study TarsosDSP, Aubio, Android AudioRecord, piano pitch characteristics. Deliver a technical design, detector comparison, and test plan.

**Phase 1 — Audio Lab:** Microphone → pitch detector → live note display. No games. Success criteria: stable real-time pitch detection, measurable latency, useful confidence.

**Phase 2 — Calibration:** calibration UI, multi-sample capture, calibration profile, quality score, validation test, recalibration.

**Phase 3 — Basic Learning:** note recognition, A/B/C/D/E lessons, correct/wrong/unclear feedback, progress tracking.

**Phase 4 — Games:** falling notes, rhythm, echo, memory, songs, performance mode, free play.

**Phase 5 — Motivation:** characters, stars, badges, streaks, daily missions, unlockables.

**Phase 6 — Parent Features:** progress dashboard, weekly summary, settings, privacy controls.

---

## 46. MVP Definition

1. Android native app
2. Microphone permission & `AudioRecord` (unprocessed source, with runtime fallback)
3. TarsosDSP pitch detection wrapper
4. Audio Lab engineering screen
5. Calibration profile setup
6. Initial five-note range (C–G, validated on Yamaha PSR-F52)
7. Individual note lessons & clear uncertainty handlers
8. One falling-note game
9. Basic progress & badges

Only after this is reliable should more games and characters be added.

---

## 47. AI Coding Agent Instructions

1. Treat this document as the source of truth.
2. Do not skip the Audio Lab.
3. Do not start with the game UI.
4. Review TarsosDSP before implementing pitch detection.
5. Review Aubio/aubio tuner concepts.
6. Keep pitch detection behind an interface.
7. Do not assume raw FFT peak detection is sufficient.
8. Never treat low-confidence detection as an incorrect note.
9. Build automated audio regression tests — both desktop (Tier 1) and on-device instrumented (Tier 2); see Section 41.
10. Do not store/upload raw microphone audio by default.
11. Keep the system offline-first.
12. Measure real latency, per-note, on physical devices — see Section 16.
13. Test on real Android devices and real pianos.
14. Document every audio-processing assumption.
15. Do not replace native Android audio processing with React Native for the MVP.
16. Do not add large dependencies (e.g. Essentia) unless there is a demonstrated requirement.
17. Prefer deterministic local processing for core recognition.
18. Every major audio-engine change must be benchmarked against the regression dataset.
19. `[NEW v3.0]` Enforce `FOREGROUND_SERVICE_TYPE_MICROPHONE` for any background/lock-screen capture path (Section 3, 38).
20. `[NEW v3.0]` Decide and encode the octave-matching policy in `NoteValidator` before building lesson logic (Section 44).
21. `[NEW v3.0]` Do not use color alone to signal correct/incorrect — pair with shape/icon/animation (Section 18, 22).

---

## 48. Most Important Engineering Rule

The application is fundamentally an **audio-recognition product**, not merely a piano game.

If the app incorrectly tells a child "You played the wrong note" when they actually played the correct note, the learning experience becomes harmful and frustrating.

**UNCERTAINTY → ASK THE CHILD TO TRY AGAIN**

not

**UNCERTAINTY → MARK WRONG**

Accuracy, calibration, confidence estimation, latency, and real-world testing must be completed before investing heavily in game content.

---

## 49. Future Enhancements

Larger keyboard recognition, chords, two-note/chord detection, pedal detection where technically feasible, MIDI/Bluetooth keyboard support, sheet music, sight reading, adaptive difficulty, personalized learning paths, additional instruments, cloud backup, parent mobile dashboard, new song/content packs, advanced rhythm analysis.

These are **NOT** required for the initial MVP.

---

## Final Product Principle

**Learn → Play → Get Accurate Feedback → Earn Rewards → Come Back → Progress**

The child should feel like they are playing a game, while underneath it is a serious, reliable piano-learning system.
