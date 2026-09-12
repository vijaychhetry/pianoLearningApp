import { describe, expect, it } from "vitest";
import { measureNoiseFloor, measureReferencePitch, mergeCalibration } from "./calibration";
import { midiToFreq } from "./note";
import { pianoTone, sineTone } from "./synth";

const SR = 44100;

function framesFrom(signal: Float32Array, count = 12, size = 2048): Float32Array[] {
  const out: Float32Array[] = [];
  let offset = 400;
  for (let i = 0; i < count && offset + size < signal.length; i++) {
    out.push(signal.slice(offset, offset + size));
    offset += 700;
  }
  return out;
}

describe("calibration", () => {
  it("sets a gate above the silence floor", () => {
    const silence = Array.from({ length: 16 }, () => {
      const frame = new Float32Array(2048);
      for (let i = 0; i < frame.length; i++) frame[i] = (Math.random() * 2 - 1) * 0.002;
      return frame;
    });
    const result = measureNoiseFloor(silence);
    expect(result.gateRms).toBeGreaterThan(result.noiseFloorRms);
    expect(result.quality).not.toBe("poor");
  });

  it("fails A4 capture when the user plays the wrong note", () => {
    const c4 = framesFrom(sineTone(midiToFreq(60), SR, 1.2));
    const result = measureReferencePitch(c4, SR);
    expect(result.quality).toBe("poor");
    expect(result.pitchOffsetCents).toBe(0);
  });

  it("measures a slightly sharp A4 and stores the cents offset", () => {
    const sharp = midiToFreq(69) * 2 ** (20 / 1200);
    const result = measureReferencePitch(framesFrom(pianoTone(sharp, SR, 1.2)), SR);
    expect(result.quality).not.toBe("poor");
    expect(result.pitchOffsetCents).toBeGreaterThan(10);
    expect(result.pitchOffsetCents).toBeLessThan(30);
  });

  it("merges noise + pitch into a usable calibration", () => {
    const silence = Array.from({ length: 12 }, () => new Float32Array(2048));
    const noise = measureNoiseFloor(silence);
    const pitch = measureReferencePitch(framesFrom(sineTone(440, SR, 1)), SR);
    const merged = mergeCalibration(noise, pitch, SR);
    expect(merged.a4Hz).toBe(440);
    expect(Number.isFinite(merged.gateRms)).toBe(true);
  });
});
