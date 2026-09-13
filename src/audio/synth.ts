/** Synthetic piano-like tones for tests and the in-app simulator. */

export function pianoTone(
  frequency: number,
  sampleRate: number,
  durationSec: number,
  options: { amplitude?: number; harmonics?: number; decay?: number } = {},
): Float32Array {
  const amplitude = options.amplitude ?? 0.35;
  const harmonics = options.harmonics ?? 6;
  const decay = options.decay ?? 2.2;
  const n = Math.floor(sampleRate * durationSec);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / sampleRate;
    let sample = 0;
    let weight = 0;
    for (let h = 1; h <= harmonics; h++) {
      const w = 1 / h;
      sample += w * Math.sin(2 * Math.PI * frequency * h * t) * Math.exp(-decay * h * 0.15 * t);
      weight += w;
    }
    const attack = Math.min(1, t / 0.008);
    out[i] = amplitude * attack * (sample / weight);
  }
  return out;
}

export function sineTone(frequency: number, sampleRate: number, durationSec: number, amplitude = 0.4): Float32Array {
  const n = Math.floor(sampleRate * durationSec);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    out[i] = amplitude * Math.sin((2 * Math.PI * frequency * i) / sampleRate);
  }
  return out;
}

export function mixNoise(signal: Float32Array, noiseRms = 0.002): Float32Array {
  const out = new Float32Array(signal.length);
  for (let i = 0; i < signal.length; i++) {
    const noise = (Math.random() * 2 - 1) * noiseRms * Math.sqrt(3);
    out[i] = signal[i] + noise;
  }
  return out;
}

export function sliceFrame(signal: Float32Array, start: number, length: number): Float32Array {
  return signal.slice(start, start + length);
}
