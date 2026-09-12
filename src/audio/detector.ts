import { computeSpectrum, detectPitchHpsFromSpectrum, resolveOctave } from "./hps";
import {
  A4_HZ,
  MAX_PIANO_MIDI,
  MIN_PIANO_MIDI,
  applyCentsOffset,
  centsOff,
  freqToMidi,
  freqToMidiFloat,
  midiToNoteName,
} from "./note";
import { detectPitchYin, rms } from "./yin";

export interface DetectorSettings {
  minRmsGate: number;
  noiseGateMultiplier: number;
  releaseGateRatio: number;
  yinThreshold: number;
  probabilityThreshold: number;
  stableFrames: number;
  releaseAfterMs: number;
  minFreq: number;
  maxFreq: number;
  centsTolerance: number;
}

export const DEFAULT_DETECTOR_SETTINGS: DetectorSettings = {
  minRmsGate: 0.008,
  noiseGateMultiplier: 4.2,
  releaseGateRatio: 0.65,
  yinThreshold: 0.12,
  probabilityThreshold: 0.55,
  stableFrames: 3,
  releaseAfterMs: 220,
  minFreq: 55,
  maxFreq: 2093,
  centsTolerance: 45,
};

export interface Calibration {
  noiseFloorRms: number;
  gateRms: number;
  pitchOffsetCents: number;
  a4Hz: number;
  sampleRate: number;
  calibratedAt: string;
  quality: "good" | "ok" | "poor";
  notes: string[];
}

export interface FrameDebug {
  rms: number;
  gate: number;
  frequency: number | null;
  yinHz: number | null;
  hpsHz: number | null;
  probability: number;
  midi: number | null;
  note: string | null;
  cents: number | null;
  accepted: boolean;
  reason: string;
}

export interface DetectorState {
  midi: number | null;
  note: string | null;
  frequency: number | null;
  cents: number | null;
  debug: FrameDebug;
}

const emptyDebug = (rmsValue: number, gate: number, reason: string): FrameDebug => ({
  rms: rmsValue,
  gate,
  frequency: null,
  yinHz: null,
  hpsHz: null,
  probability: 0,
  midi: null,
  note: null,
  cents: null,
  accepted: false,
  reason,
});

export class NoteDetector {
  private settings: DetectorSettings;
  private calibration: Calibration | null;
  private candidateMidi: number | null = null;
  private candidateFrames = 0;
  private currentMidi: number | null = null;
  private lastSignalAt = 0;

  constructor(settings: DetectorSettings = DEFAULT_DETECTOR_SETTINGS, calibration: Calibration | null = null) {
    this.settings = settings;
    this.calibration = calibration;
  }

  setCalibration(calibration: Calibration | null): void {
    this.calibration = calibration;
  }

  setSettings(settings: Partial<DetectorSettings>): void {
    this.settings = { ...this.settings, ...settings };
  }

  reset(): void {
    this.candidateMidi = null;
    this.candidateFrames = 0;
    this.currentMidi = null;
    this.lastSignalAt = 0;
  }

  process(samples: Float32Array, sampleRate: number, now = performance.now()): DetectorState {
    const level = rms(samples);
    const noiseFloor = this.calibration?.noiseFloorRms ?? 0.0015;
    const gate = Math.max(
      this.settings.minRmsGate,
      noiseFloor * this.settings.noiseGateMultiplier,
    );
    const releaseGate = gate * this.settings.releaseGateRatio;
    const a4Hz = this.calibration?.a4Hz ?? A4_HZ;

    if (level < (this.currentMidi === null ? gate : releaseGate)) {
      if (this.currentMidi !== null && now - this.lastSignalAt >= this.settings.releaseAfterMs) {
        this.reset();
      }
      return {
        midi: this.currentMidi,
        note: this.currentMidi === null ? null : midiToNoteName(this.currentMidi),
        frequency: null,
        cents: null,
        debug: emptyDebug(level, gate, "below_gate"),
      };
    }

    const yin = detectPitchYin(samples, sampleRate, {
      threshold: this.settings.yinThreshold,
      minFreq: this.settings.minFreq,
      maxFreq: this.settings.maxFreq,
    });

    if (yin.frequency === null || yin.probability < this.settings.probabilityThreshold) {
      if (this.currentMidi !== null && now - this.lastSignalAt >= this.settings.releaseAfterMs) {
        this.reset();
      }
      return {
        midi: this.currentMidi,
        note: this.currentMidi === null ? null : midiToNoteName(this.currentMidi),
        frequency: null,
        cents: null,
        debug: {
          ...emptyDebug(level, gate, "low_confidence"),
          yinHz: yin.frequency,
          probability: yin.probability,
        },
      };
    }

    const spec = computeSpectrum(samples, sampleRate);
    const hpsHz = detectPitchHpsFromSpectrum(spec, {
      minFreq: this.settings.minFreq,
      maxFreq: this.settings.maxFreq,
    });
    const resolved = resolveOctave(yin.frequency, hpsHz, spec);
    const corrected = applyCentsOffset(resolved, -(this.calibration?.pitchOffsetCents ?? 0));
    const midi = freqToMidi(corrected, a4Hz);

    if (midi < MIN_PIANO_MIDI || midi > MAX_PIANO_MIDI) {
      return {
        midi: this.currentMidi,
        note: this.currentMidi === null ? null : midiToNoteName(this.currentMidi),
        frequency: corrected,
        cents: null,
        debug: {
          ...emptyDebug(level, gate, "out_of_range"),
          frequency: corrected,
          yinHz: yin.frequency,
          hpsHz,
          probability: yin.probability,
        },
      };
    }

    const cents = centsOff(corrected, midi, a4Hz);
    if (Math.abs(cents) > this.settings.centsTolerance) {
      return {
        midi: this.currentMidi,
        note: this.currentMidi === null ? null : midiToNoteName(this.currentMidi),
        frequency: corrected,
        cents,
        debug: {
          rms: level,
          gate,
          frequency: corrected,
          yinHz: yin.frequency,
          hpsHz,
          probability: yin.probability,
          midi,
          note: midiToNoteName(midi),
          cents,
          accepted: false,
          reason: "off_pitch",
        },
      };
    }

    this.lastSignalAt = now;
    if (this.candidateMidi === midi) {
      this.candidateFrames += 1;
    } else {
      this.candidateMidi = midi;
      this.candidateFrames = 1;
    }

    const accepted = this.candidateFrames >= this.settings.stableFrames;
    if (accepted) {
      this.currentMidi = midi;
    }

    return {
      midi: this.currentMidi,
      note: this.currentMidi === null ? null : midiToNoteName(this.currentMidi),
      frequency: corrected,
      cents,
      debug: {
        rms: level,
        gate,
        frequency: corrected,
        yinHz: yin.frequency,
        hpsHz,
        probability: yin.probability,
        midi,
        note: midiToNoteName(midi),
        cents,
        accepted,
        reason: accepted ? "locked" : `stabilizing_${this.candidateFrames}/${this.settings.stableFrames}`,
      },
    };
  }

  getMidiFloatHint(freq: number): number {
    const a4Hz = this.calibration?.a4Hz ?? A4_HZ;
    const corrected = applyCentsOffset(freq, -(this.calibration?.pitchOffsetCents ?? 0));
    return freqToMidiFloat(corrected, a4Hz);
  }
}
