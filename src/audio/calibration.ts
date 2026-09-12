import { A4_HZ, centsOff, freqToMidi } from "./note";
import { rms } from "./yin";
import type { Calibration } from "./detector";
import { detectPitchYin } from "./yin";
import { computeSpectrum, detectPitchHpsFromSpectrum, resolveOctave } from "./hps";

const STORAGE_KEY = "piano-learning-calibration-v1";

export function loadCalibration(): Calibration | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Calibration;
    if (typeof parsed.gateRms !== "number" || typeof parsed.pitchOffsetCents !== "number") {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveCalibration(calibration: Calibration): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(calibration));
}

export function clearCalibration(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export function percentile(values: number[], p: number): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.floor((p / 100) * sorted.length)));
  return sorted[idx];
}

export function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

export function measureNoiseFloor(
  frames: Float32Array[],
  multiplier = 4.2,
  minGate = 0.008,
): { noiseFloorRms: number; gateRms: number; quality: Calibration["quality"]; notes: string[] } {
  const levels = frames.map((frame) => rms(frame)).filter((v) => Number.isFinite(v));
  const notes: string[] = [];
  if (levels.length < 8) {
    return {
      noiseFloorRms: 0.002,
      gateRms: minGate,
      quality: "poor",
      notes: ["Not enough silence frames. Keep the room quiet and try again."],
    };
  }
  const noiseFloorRms = percentile(levels, 80);
  const peak = percentile(levels, 99);
  if (peak > noiseFloorRms * 6) {
    notes.push("Heard loud sounds during silence capture. Recalibrate in a quieter moment.");
  }
  const gateRms = Math.max(minGate, noiseFloorRms * multiplier);
  let quality: Calibration["quality"] = "good";
  if (noiseFloorRms > 0.04) {
    quality = "poor";
    notes.push("Microphone noise floor is high. Move closer to the piano or lower OS mic boost.");
  } else if (noiseFloorRms > 0.015) {
    quality = "ok";
    notes.push("Room is a bit noisy. Detection still works, but quiet notes may be missed.");
  }
  if (notes.length === 0) notes.push("Silence floor looks clean.");
  return { noiseFloorRms, gateRms, quality, notes };
}

export function measureReferencePitch(
  frames: Float32Array[],
  sampleRate: number,
  expectedHz = A4_HZ,
): { measuredHz: number; pitchOffsetCents: number; a4Hz: number; quality: Calibration["quality"]; notes: string[] } {
  const notes: string[] = [];
  const freqs: number[] = [];
  for (const frame of frames) {
    const yin = detectPitchYin(frame, sampleRate, { minFreq: 80, maxFreq: 1200, threshold: 0.15 });
    if (yin.frequency === null || yin.probability < 0.5) continue;
    const spec = computeSpectrum(frame, sampleRate);
    const hpsHz = detectPitchHpsFromSpectrum(spec, { minFreq: 80, maxFreq: 1200 });
    freqs.push(resolveOctave(yin.frequency, hpsHz, spec));
  }
  if (freqs.length < 6) {
    return {
      measuredHz: expectedHz,
      pitchOffsetCents: 0,
      a4Hz: A4_HZ,
      quality: "poor",
      notes: ["Could not lock onto A4. Play a steady A4 (the A above middle C) and hold it."],
    };
  }
  const measuredHz = median(freqs);
  const midi = freqToMidi(measuredHz, A4_HZ);
  const centsFromA4 = 1200 * Math.log2(measuredHz / expectedHz);
  const centsFromNearest = centsOff(measuredHz, midi, A4_HZ);

  if (Math.abs(centsFromA4) > 250) {
    notes.push(
      `Heard ${measuredHz.toFixed(1)} Hz, which is not near A4 (440 Hz). Play A4, not ${midi}.`,
    );
    return {
      measuredHz,
      pitchOffsetCents: 0,
      a4Hz: A4_HZ,
      quality: "poor",
      notes,
    };
  }

  let quality: Calibration["quality"] = "good";
  if (Math.abs(centsFromNearest) > 25) {
    quality = "ok";
    notes.push("Pitch wandered during capture. Hold the note steadily.");
  }
  if (Math.abs(centsFromA4) > 40) {
    quality = quality === "good" ? "ok" : quality;
    notes.push(`Piano appears ${centsFromA4 > 0 ? "sharp" : "flat"} by ${Math.abs(centsFromA4).toFixed(0)} cents vs A440.`);
  }
  if (notes.length === 0) notes.push("A4 reference locked. Offset will be applied to every detection.");

  return {
    measuredHz,
    pitchOffsetCents: centsFromA4,
    a4Hz: expectedHz,
    quality,
    notes,
  };
}

export function mergeCalibration(
  noise: ReturnType<typeof measureNoiseFloor>,
  pitch: ReturnType<typeof measureReferencePitch>,
  sampleRate: number,
): Calibration {
  const qualities = [noise.quality, pitch.quality];
  const quality: Calibration["quality"] = qualities.includes("poor")
    ? "poor"
    : qualities.includes("ok")
      ? "ok"
      : "good";
  return {
    noiseFloorRms: noise.noiseFloorRms,
    gateRms: noise.gateRms,
    pitchOffsetCents: pitch.pitchOffsetCents,
    a4Hz: pitch.a4Hz,
    sampleRate,
    calibratedAt: new Date().toISOString(),
    quality,
    notes: [...noise.notes, ...pitch.notes],
  };
}

export function defaultCalibration(): Calibration {
  return {
    noiseFloorRms: 0.002,
    gateRms: 0.008,
    pitchOffsetCents: 0,
    a4Hz: A4_HZ,
    sampleRate: 48000,
    calibratedAt: new Date().toISOString(),
    quality: "ok",
    notes: ["Using factory defaults (A440, moderate noise gate). Run calibration for better accuracy."],
  };
}
