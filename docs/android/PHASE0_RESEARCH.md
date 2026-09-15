# Phase 0 — Audio research & technical design

Kids Piano (Android). Canonical product spec: `docs/KIDS_PIANO_MASTER_SPEC.md`.

This document is the Phase 0 deliverable: how we start, what we reuse, what we will not do yet.

## Decision: new Android project, not the React site

The existing Vite app stays as a **browser lab**. Production is `android/` (Kotlin, Compose, `AudioRecord`).

## Detector comparison (desk research)

| Detector | Strength | Piano risk |
| --- | --- | --- |
| YIN (TarsosDSP + our port) | Stable fundamental, confidence | Octave-up on rich harmonics |
| MPM / McLeod | Often better on low notes | Still monophonic |
| Raw FFT peak | Fast | Locks on 2nd harmonic — **not sufficient alone** (spec §47.7) |
| Essentia | Heavy | Out of MVP (spec §4) |

**v1 choice:** `PitchDetector` interface. Default implementation: YIN. Optional second opinion: Harmonic Product Spectrum used only to fold **one octave down** when the lower peak is strong (same rule we proved in the web lab). Swap in TarsosDSP `Yin` on device without changing UI.

## AudioRecord plan

- Request `UNPROCESSED`; if init fails, fall back to `VOICE_RECOGNITION` / `MIC` and log the source actually applied.
- Query output sample rate from `AudioManager`; do not hardcode 44.1 kHz.
- Mono 16-bit PCM, buffer sized from `getMinBufferSize`, hop ~50% overlap.
- Capture thread at `THREAD_PRIORITY_AUDIO`.
- Bounded queue, **drop-oldest** (spec §3). Capacity: 4 frames.
- Phase 1 Audio Lab keeps the screen on; **no** background capture yet. Foreground-service mic type is required only if we later listen with the screen off (spec §38).

## Latency (spec §16)

Do not chase a flat &lt;100 ms for every note. Report **per-note** latency in Audio Lab. Processing overhead beyond the YIN window should stay under ~40 ms.

| Note | Period | Practical window |
| --- | --- | --- |
| C4 (~262 Hz) | ~3.8 ms | ~40–80 ms of audio |
| C5 | ~1.9 ms | shorter OK |
| A1 | ~18 ms | needs a long window; **out of MVP range** |

MVP range: **C4–G4** (five white keys), validated later on Yamaha PSR-F52.

## Octave policy (spec §44) — decided

For Level 1 five-key lessons:

- Same MIDI → `CORRECT`
- Same pitch class, different octave (C4 vs C5) → `CORRECT_OCTAVE_MISMATCH` (“Right letter, try the other C”)
- Clear other pitch class, high confidence → `INCORRECT`
- Low confidence / noise / two peaks → `UNCLEAR` or `AMBIGUOUS` — **never INCORRECT**

## Calibration (Phase 2, not this slice)

Not silence+A4. Per-note statistical profiles for C–G, then a blind C–G test. Room/DataStore. Audio Lab in Phase 1 only shows live pitch so we can **collect** recordings.

## Test plan

Mapped acceptance criteria and JVM test method names: [`ACCEPTANCE_CRITERIA.md`](ACCEPTANCE_CRITERIA.md).

**Tier 1 (this environment, JVM):** frequency↔MIDI, YIN on synthetic sines and harmonic tones (Hz, not just MIDI), polyphony → `AMBIGUOUS`, debounce state machine, `NoteValidator` (octave mismatch, between-note, low-confidence never wrong), calibration median/IQR/C–G completeness, `RecognitionPipeline`.

**Tier 2 (physical device, later):** `AudioRecord` source actually applied, sample rate, per-note latency, PSR-F52 at music-stand distance. Not covered by empty instrumented tests.

## Phase 1 success (Audio Lab only)

- Grant mic → live level, Hz, MIDI, note name, confidence, status, latency, event log.
- No games, no characters, no lesson tree.
- Child-facing copy is not required; this screen is for engineering (spec §15).

## What we are explicitly not starting

Games, characters, parent dashboard, React Native, Essentia, Play Store listing.
