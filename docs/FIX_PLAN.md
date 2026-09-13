# Plan: fix microphone piano-note recognition and calibration

Repository state at review time: `main` contained only `README.md`. There was no
pitch engine to patch. This plan is the design that the implementation follows.

## Failure modes

1. **Octave errors** — acoustic pianos are harmonic-rich. Time-domain pitch
   trackers (YIN, autocorrelation) often lock on 2f, so A4 (440 Hz) is reported
   as A5. Fix: run Harmonic Product Spectrum in parallel and, when the ratio is
   ~2.0 or ~3.0, keep the lower frequency.
2. **Broken noise gate** — a hardcoded RMS threshold either swallows quiet notes
   or lets room noise through. Fix: 2 s silence capture; gate = max(minGate,
   noiseFloor × 4.2). Reject the capture if the “silence” contains peaks.
3. **Calibration that cannot fail closed** — many UIs save whatever the mic
   heard, including C4 labelled as A4. Fix: A4 capture must land within 250 cents
   of 440 Hz or quality is `poor` and the offset is not applied.
4. **Browser audio “helpers”** — `echoCancellation`, `noiseSuppression`, and
   `autoGainControl` smear piano transients. Fix: request the raw mic stream.
5. **Unstable MIDI** — mapping every frame’s frequency to the nearest note
   chatters. Fix: lock only after three consecutive frames agree; release after
   220 ms below the gate.
6. **Untuned piano** — a piano 20–40 cents sharp is “always wrong”. Fix: store
   A4 cents offset and subtract it before MIDI rounding.

## Implementation slices

1. Pure note math (`note.ts`) and unit tests.
2. YIN + HPS + `NoteDetector` state machine.
3. Calibration merge (`measureNoiseFloor`, `measureReferencePitch`).
4. `getUserMedia` session + simulator that reuses the same detector.
5. UI: Listen / Calibrate / Practice + 3-octave keyboard.
6. Synthetic fixtures covering C4–C5 piano tones, silence, sharp A4, wrong-note A4.

## Out of scope (later)

- Polyphonic chord recognition
- MIDI keyboard input
- Full 88-key range (engine is tuned A1–C7; UI shows C3–C6)
- Teacher accounts / song library
