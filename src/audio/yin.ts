/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002).
 * Returns the fundamental frequency and a 0–1 confidence score.
 */
export interface YinResult {
  frequency: number | null;
  probability: number;
  tau: number | null;
}

export interface YinOptions {
  threshold?: number;
  minFreq?: number;
  maxFreq?: number;
}

function parabolicInterpolation(buffer: Float32Array, tau: number): number {
  const x0 = tau < 1 ? tau : tau - 1;
  const x2 = tau + 1 < buffer.length ? tau + 1 : tau;
  if (x0 === tau) return tau;
  if (x2 === tau) return tau;
  const s0 = buffer[x0];
  const s1 = buffer[tau];
  const s2 = buffer[x2];
  const denom = 2 * s1 - s2 - s0;
  if (denom === 0) return tau;
  return tau + (s2 - s0) / (2 * denom);
}

export function detectPitchYin(
  samples: Float32Array,
  sampleRate: number,
  options: YinOptions = {},
): YinResult {
  const threshold = options.threshold ?? 0.12;
  const minFreq = options.minFreq ?? 55; // A1 — beginners rarely go lower
  const maxFreq = options.maxFreq ?? 2093; // C7
  const half = Math.floor(samples.length / 2);
  const tauMin = Math.max(2, Math.floor(sampleRate / maxFreq));
  const tauMax = Math.min(half - 1, Math.floor(sampleRate / minFreq));
  if (tauMax <= tauMin + 2) {
    return { frequency: null, probability: 0, tau: null };
  }

  const yin = new Float32Array(tauMax + 1);
  for (let tau = 1; tau <= tauMax; tau++) {
    let sum = 0;
    for (let i = 0; i < half; i++) {
      const delta = samples[i] - samples[i + tau];
      sum += delta * delta;
    }
    yin[tau] = sum;
  }

  yin[0] = 1;
  let runningSum = 0;
  for (let tau = 1; tau <= tauMax; tau++) {
    runningSum += yin[tau];
    yin[tau] = runningSum === 0 ? 1 : (yin[tau] * tau) / runningSum;
  }

  let tauEstimate = -1;
  for (let tau = tauMin; tau <= tauMax; tau++) {
    if (yin[tau] < threshold) {
      while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) {
        tau += 1;
      }
      tauEstimate = tau;
      break;
    }
  }

  if (tauEstimate === -1) {
    let best = 1;
    let bestTau = -1;
    for (let tau = tauMin; tau <= tauMax; tau++) {
      if (yin[tau] < best) {
        best = yin[tau];
        bestTau = tau;
      }
    }
    if (bestTau === -1 || best > 0.35) {
      return { frequency: null, probability: 0, tau: null };
    }
    tauEstimate = bestTau;
  }

  const betterTau = parabolicInterpolation(yin, tauEstimate);
  if (betterTau <= 0) {
    return { frequency: null, probability: 0, tau: null };
  }
  const probability = Math.max(0, Math.min(1, 1 - yin[tauEstimate]));
  return {
    frequency: sampleRate / betterTau,
    probability,
    tau: betterTau,
  };
}

export function rms(samples: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < samples.length; i++) {
    sum += samples[i] * samples[i];
  }
  return Math.sqrt(sum / samples.length);
}
