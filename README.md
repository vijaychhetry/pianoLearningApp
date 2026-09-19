# Piano Learning App

Two things live in this repo:

1. **Product (start here):** native Android app in [`android/`](android/) — Level 1 Find this key with an on-screen keyboard, plus grown-up Setup / Calibrate / Files / Audio Lab. Sideload [`releases/KidsPiano-debug-0.5.3.apk`](releases/KidsPiano-debug-0.5.3.apk).
   Spec: [`docs/KIDS_PIANO_MASTER_SPEC.md`](docs/KIDS_PIANO_MASTER_SPEC.md).
   Design for this slice: [`docs/superpowers/specs/2026-09-18-level-1-find-key-design.md`](docs/superpowers/specs/2026-09-18-level-1-find-key-design.md).
2. **Web prototype:** the Vite/React tuner at the repo root. Lab only, not the kids app.

Open **`android/`** in Android Studio, run `app`, allow the microphone, play one piano key.

Details: [`android/README.md`](android/README.md) and [`docs/android/PHASE0_RESEARCH.md`](docs/android/PHASE0_RESEARCH.md).

## Why recognition usually fails

| Symptom | Typical cause | What this app does |
| --- | --- | --- |
| Wrong octave (A4 heard as A5) | Piano harmonics trick autocorrelation/YIN | YIN + Harmonic Product Spectrum, prefer the lower match |
| No note detected | Noise gate too high, or AGC/echo cancellation on | Silence calibration; `echoCancellation`/`noiseSuppression`/`autoGainControl` disabled |
| Notes jump around | Single noisy FFT peak | Require 3 stable frames before lock; 45-cent tolerance |
| Calibration does nothing | Silence captured while playing, or A4 never verified | Wizard refuses bad captures and reports quality `good` / `ok` / `poor` |
| Piano not at A440 | Offset never measured | A4 reference stored as cents and applied to every detection |

The original GitHub repo only contained a README, so this implementation is the
recognition + calibration engine rather than a patch on missing source.

## Run

```bash
npm install
npm test
npm run dev
```

Open the printed local URL. Chrome/Edge required for microphone access (HTTPS or
`localhost`).

## Use

1. **Calibrate**
   - Enable the microphone (or Simulator if you have no piano).
   - Capture silence (room noise floor → gate).
   - Hold **A4** (A above middle C). The app stores how far the piano is from 440 Hz.
2. **Listen** — play single notes. The large pitch display and keyboard should follow.
3. **Practice** — play the highlighted note.

Clicking the on-screen keyboard injects a harmonic piano-like tone into the same
detector used for the microphone, so you can verify the engine without hardware.

## Audio pipeline

```
getUserMedia (no AGC / echo cancel)
        → AnalyserNode time-domain frames (2048)
        → RMS noise gate (from calibration)
        → YIN fundamental
        → HPS octave check
        → cents offset from A4 calibration
        → MIDI note, lock after 3 agreeing frames
```

Core files:

- `src/audio/yin.ts` — YIN detector
- `src/audio/hps.ts` — harmonic product spectrum + octave resolver
- `src/audio/detector.ts` — gate, stability, lock/release
- `src/audio/calibration.ts` — silence floor + A4 reference
- `src/audio/note.ts` — MIDI / Hz / cents

## Tests

`npm test` runs synthetic sine and piano-tone fixtures through the detector,
including a sharp-piano calibration case and a wrong-note A4 rejection.
