import { describe, expect, it } from "vitest";
import { NoteDetector } from "./detector";
import { midiToFreq } from "./note";
import { pianoTone, sineTone, sliceFrame } from "./synth";
import { detectPitchHps, resolveOctave } from "./hps";
import { detectPitchYin } from "./yin";

const SR = 44100;

function lockNote(midi: number, kind: "sine" | "piano" = "piano"): number | null {
  const detector = new NoteDetector();
  const freq = midiToFreq(midi);
  const signal = kind === "sine" ? sineTone(freq, SR, 0.8) : pianoTone(freq, SR, 0.8, { harmonics: 7 });
  let midiOut: number | null = null;
  let now = 0;
  for (let start = 800; start + 2048 < signal.length; start += 512) {
    now += 16;
    const state = detector.process(sliceFrame(signal, start, 2048), SR, now);
    if (state.midi !== null) midiOut = state.midi;
  }
  return midiOut;
}

describe("note detector", () => {
  it("locks C4, E4, G4, A4, C5 piano tones", () => {
    for (const midi of [60, 64, 67, 69, 72]) {
      expect(lockNote(midi, "piano")).toBe(midi);
    }
  });

  it("does not lock silence", () => {
    const detector = new NoteDetector();
    const silence = new Float32Array(2048);
    const state = detector.process(silence, SR, 1);
    expect(state.midi).toBeNull();
    expect(state.debug.reason).toBe("below_gate");
  });

  it("applies a sharp-piano calibration offset", () => {
    const detector = new NoteDetector();
    detector.setCalibration({
      noiseFloorRms: 0.001,
      gateRms: 0.008,
      pitchOffsetCents: 50,
      a4Hz: 440,
      sampleRate: SR,
      calibratedAt: new Date().toISOString(),
      quality: "ok",
      notes: [],
    });
    const sharpA4 = midiToFreq(69) * 2 ** (50 / 1200);
    const signal = sineTone(sharpA4, SR, 0.7);
    let midiOut: number | null = null;
    let now = 0;
    for (let start = 400; start + 2048 < signal.length; start += 512) {
      now += 16;
      const state = detector.process(sliceFrame(signal, start, 2048), SR, now);
      if (state.midi !== null) midiOut = state.midi;
    }
    expect(midiOut).toBe(69);
  });
});

describe("octave correction", () => {
  it("prefers the lower frequency when YIN is an octave high", () => {
    expect(resolveOctave(880, 440)).toBe(440);
  });

  it("keeps YIN when HPS agrees", () => {
    expect(resolveOctave(440, 442)).toBe(440);
  });

  it("HPS recovers the fundamental of a harmonic-rich low note", () => {
    const freq = midiToFreq(55); // G3
    const tone = pianoTone(freq, SR, 0.6, { harmonics: 8, decay: 0.8, amplitude: 0.45 });
    const frame = sliceFrame(tone, 1500, 2048);
    const yin = detectPitchYin(frame, SR);
    const hps = detectPitchHps(frame, SR);
    expect(hps).not.toBeNull();
    const resolved = resolveOctave(yin.frequency ?? 0, hps);
    const midi = Math.round(69 + 12 * Math.log2(resolved / 440));
    expect(midi).toBe(55);
  });
});
