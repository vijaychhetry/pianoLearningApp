import { describe, expect, it } from "vitest";
import { midiToFreq } from "./note";
import { pianoTone, sineTone } from "./synth";
import { detectPitchYin } from "./yin";

const SR = 44100;

function frameOf(signal: Float32Array, start = 2000, length = 2048): Float32Array {
  return signal.slice(start, start + length);
}

describe("YIN pitch detector", () => {
  it.each([
    ["A4", 69],
    ["C4", 60],
    ["E4", 64],
    ["C5", 72],
    ["G3", 55],
  ])("detects sine %s", (_name, midi) => {
    const freq = midiToFreq(midi);
    const result = detectPitchYin(frameOf(sineTone(freq, SR, 0.4)), SR);
    expect(result.frequency).not.toBeNull();
    expect(result.frequency!).toBeCloseTo(freq, 0);
    expect(result.probability).toBeGreaterThan(0.8);
  });

  it("returns null for silence", () => {
    const result = detectPitchYin(new Float32Array(2048), SR);
    expect(result.frequency).toBeNull();
  });
});

describe("piano-like tones", () => {
  it("detects the fundamental of a harmonic-rich A4", () => {
    const freq = midiToFreq(69);
    const tone = pianoTone(freq, SR, 0.5, { harmonics: 8, decay: 1.4 });
    const result = detectPitchYin(frameOf(tone), SR);
    expect(result.frequency).not.toBeNull();
    expect(result.frequency!).toBeCloseTo(freq, 0);
  });
});
