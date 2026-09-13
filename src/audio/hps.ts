/**
 * Harmonic Product Spectrum and octave repair.
 * YIN is the primary estimator. HPS is only allowed to fold YIN down by one
 * octave when the lower frequency itself is a strong spectral peak — otherwise
 * HPS's f/2 and f/3 subharmonics win on sine-like frames.
 */

function nextPow2(n: number): number {
  let p = 1;
  while (p < n) p *= 2;
  return p;
}

function hann(n: number): Float32Array {
  const w = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    w[i] = 0.5 * (1 - Math.cos((2 * Math.PI * i) / (n - 1)));
  }
  return w;
}

function fftRadix2(real: Float32Array, imag: Float32Array): void {
  const n = real.length;
  let j = 0;
  for (let i = 0; i < n; i++) {
    if (i < j) {
      const tr = real[i];
      real[i] = real[j];
      real[j] = tr;
      const ti = imag[i];
      imag[i] = imag[j];
      imag[j] = ti;
    }
    let m = n >> 1;
    while (m >= 1 && j >= m) {
      j -= m;
      m >>= 1;
    }
    j += m;
  }
  for (let size = 2; size <= n; size <<= 1) {
    const half = size >> 1;
    const step = (2 * Math.PI) / size;
    for (let i = 0; i < n; i += size) {
      for (let k = 0; k < half; k++) {
        const angle = step * k;
        const wr = Math.cos(angle);
        const wi = -Math.sin(angle);
        const evenR = real[i + k];
        const evenI = imag[i + k];
        const oddR = real[i + k + half];
        const oddI = imag[i + k + half];
        const tr = wr * oddR - wi * oddI;
        const ti = wr * oddI + wi * oddR;
        real[i + k] = evenR + tr;
        imag[i + k] = evenI + ti;
        real[i + k + half] = evenR - tr;
        imag[i + k + half] = evenI - ti;
      }
    }
  }
}

export interface Spectrum {
  mag: Float32Array;
  n: number;
  sampleRate: number;
}

export function computeSpectrum(samples: Float32Array, sampleRate: number): Spectrum {
  const n = nextPow2(samples.length);
  const real = new Float32Array(n);
  const imag = new Float32Array(n);
  const window = hann(samples.length);
  for (let i = 0; i < samples.length; i++) {
    real[i] = samples[i] * window[i];
  }
  fftRadix2(real, imag);
  const bins = n / 2;
  const mag = new Float32Array(bins);
  for (let i = 0; i < bins; i++) {
    mag[i] = Math.hypot(real[i], imag[i]);
  }
  return { mag, n, sampleRate };
}

function magAt(spec: Spectrum, freq: number): number {
  const bin = Math.round((freq * spec.n) / spec.sampleRate);
  if (bin <= 0 || bin >= spec.mag.length) return 0;
  return spec.mag[bin];
}

export function detectPitchHps(
  samples: Float32Array,
  sampleRate: number,
  options: { minFreq?: number; maxFreq?: number; harmonics?: number } = {},
): number | null {
  const spec = computeSpectrum(samples, sampleRate);
  return detectPitchHpsFromSpectrum(spec, options);
}

export function detectPitchHpsFromSpectrum(
  spec: Spectrum,
  options: { minFreq?: number; maxFreq?: number; harmonics?: number } = {},
): number | null {
  const minFreq = options.minFreq ?? 55;
  const maxFreq = options.maxFreq ?? 2093;
  const harmonics = options.harmonics ?? 4;
  const { mag, n, sampleRate } = spec;
  const bins = mag.length;
  const hps = new Float32Array(bins);
  for (let i = 0; i < bins; i++) hps[i] = mag[i];
  for (let h = 2; h <= harmonics; h++) {
    const limit = Math.floor(bins / h);
    for (let i = 0; i < limit; i++) {
      hps[i] *= mag[i * h];
    }
    for (let i = limit; i < bins; i++) hps[i] = 0;
  }

  const minBin = Math.max(1, Math.floor((minFreq * n) / sampleRate));
  const maxBin = Math.min(bins - 1, Math.ceil((maxFreq * n) / sampleRate));
  let peakBin = minBin;
  let peakVal = 0;
  for (let i = minBin; i <= maxBin; i++) {
    if (hps[i] > peakVal) {
      peakVal = hps[i];
      peakBin = i;
    }
  }
  if (peakVal <= 0) return null;

  const y0 = hps[Math.max(minBin, peakBin - 1)];
  const y1 = hps[peakBin];
  const y2 = hps[Math.min(maxBin, peakBin + 1)];
  const denom = 2 * y1 - y0 - y2;
  const delta = denom === 0 ? 0 : (0.5 * (y0 - y2)) / denom;
  return ((peakBin + delta) * sampleRate) / n;
}

/**
 * Fold YIN down by one octave only when HPS agrees AND the lower peak is strong.
 * Do not fold by 3x: HPS subharmonics of a single tone sit at f/3.
 */
export function resolveOctave(
  yinHz: number,
  hpsHz: number | null,
  spec?: Spectrum,
): number {
  if (!hpsHz || !Number.isFinite(hpsHz) || yinHz <= 0) return yinHz;
  const ratio = yinHz / hpsHz;
  if (ratio > 0.94 && ratio < 1.06) return yinHz;
  const octaveHigh = ratio > 1.87 && ratio < 2.14;
  if (!octaveHigh) return yinHz;
  if (!spec) return hpsHz;
  const low = magAt(spec, hpsHz);
  const high = magAt(spec, yinHz);
  if (high <= 0) return hpsHz;
  return low >= high * 0.22 ? hpsHz : yinHz;
}
