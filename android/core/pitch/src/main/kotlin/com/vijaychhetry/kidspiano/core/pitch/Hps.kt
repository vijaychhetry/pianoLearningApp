package com.vijaychhetry.kidspiano.core.pitch

internal data class Spectrum(val mag: DoubleArray, val n: Int, val sampleRate: Int)

internal fun computeSpectrum(samples: FloatArray, sampleRate: Int): Spectrum {
    val n = nextPow2(samples.size)
    val real = DoubleArray(n)
    val imag = DoubleArray(n)
    val last = (samples.size - 1).coerceAtLeast(1)
    for (i in samples.indices) {
        val w = 0.5 * (1 - Math.cos(2 * Math.PI * i / last))
        real[i] = samples[i] * w
    }
    fft(real, imag)
    val mag = DoubleArray(n / 2)
    for (i in mag.indices) mag[i] = Math.hypot(real[i], imag[i])
    return Spectrum(mag, n, sampleRate)
}

fun detectPitchHps(
    samples: FloatArray,
    sampleRate: Int,
    minFreq: Double = 130.0,
    maxFreq: Double = 800.0,
    harmonics: Int = 4,
): Double? = detectPitchHps(computeSpectrum(samples, sampleRate), minFreq, maxFreq, harmonics)

internal fun detectPitchHps(
    spec: Spectrum,
    minFreq: Double,
    maxFreq: Double,
    harmonics: Int,
): Double? {
    val mag = spec.mag
    val bins = mag.size
    val hps = DoubleArray(bins) { mag[it] }
    for (h in 2..harmonics) {
        val limit = bins / h
        for (i in 0 until limit) hps[i] *= mag[i * h]
        for (i in limit until bins) hps[i] = 0.0
    }
    val minBin = maxOf(1, Math.floor(minFreq * spec.n / spec.sampleRate).toInt())
    val maxBin = minOf(bins - 1, Math.ceil(maxFreq * spec.n / spec.sampleRate).toInt())
    var peakBin = minBin
    var peakVal = 0.0
    for (i in minBin..maxBin) {
        if (hps[i] > peakVal) {
            peakVal = hps[i]
            peakBin = i
        }
    }
    if (peakVal <= 0) return null
    val y0 = hps[maxOf(minBin, peakBin - 1)]
    val y1 = hps[peakBin]
    val y2 = hps[minOf(maxBin, peakBin + 1)]
    val denom = 2 * y1 - y0 - y2
    val delta = if (denom == 0.0) 0.0 else 0.5 * (y0 - y2) / denom
    return (peakBin + delta) * spec.sampleRate / spec.n
}

/**
 * Two strong peaks that are not integer harmonics of each other → polyphony.
 * Harmonic-rich single notes (2f, 3f, …) must not trip this.
 */
internal fun isPolyphonic(
    spec: Spectrum,
    minFreq: Double,
    maxFreq: Double,
    relativePeak: Double = 0.32,
    minSeparationCents: Double = 90.0,
): Boolean {
    val mag = spec.mag
    val minBin = maxOf(2, Math.floor(minFreq * spec.n / spec.sampleRate).toInt())
    val maxBin = minOf(mag.size - 2, Math.ceil(maxFreq * spec.n / spec.sampleRate).toInt())
    var maxMag = 0.0
    for (i in minBin..maxBin) if (mag[i] > maxMag) maxMag = mag[i]
    if (maxMag <= 0.0) return false
    val floor = maxMag * relativePeak
    val peaks = ArrayList<Pair<Int, Double>>(8)
    for (i in (minBin + 1) until maxBin) {
        if (mag[i] < floor) continue
        if (mag[i] <= mag[i - 1] || mag[i] < mag[i + 1]) continue
        val last = peaks.lastOrNull()
        if (last != null && i - last.first < 3) {
            if (mag[i] > last.second) peaks[peaks.lastIndex] = i to mag[i]
        } else {
            peaks.add(i to mag[i])
        }
    }
    if (peaks.size < 2) return false
    peaks.sortByDescending { it.second }
    fun hz(bin: Int): Double = bin.toDouble() * spec.sampleRate / spec.n
    val f0 = hz(peaks[0].first)
    val m0 = peaks[0].second
    val limit = minOf(peaks.size, 6)
    for (k in 1 until limit) {
        val f = hz(peaks[k].first)
        val m = peaks[k].second
        if (m < m0 * relativePeak) continue
        val cents = 1200.0 * kotlin.math.abs(Math.log(f / f0) / Math.log(2.0))
        if (!sameHarmonicSeries(f, f0, spec, maxMag, minFreq) && cents > minSeparationCents) {
            return true
        }
    }
    return false
}

/**
 * 2f vs 3f is a fifth (3:2), not an integer multiple of the louder peak.
 * That is still one piano note if a shared fundamental has energy.
 * C4+E4 (5:4 of a sub-audio C2) must stay polyphonic.
 */
internal fun sameHarmonicSeries(
    a: Double,
    b: Double,
    spec: Spectrum,
    maxMag: Double,
    minFreq: Double,
): Boolean {
    val ratio = maxOf(a, b) / minOf(a, b)
    val nearest = Math.round(ratio).toDouble()
    if (nearest >= 2.0 && nearest <= 8.0 && kotlin.math.abs(ratio - nearest) < 0.10) return true
    for (n1 in 1..8) {
        for (n2 in 1..8) {
            if (n1 == n2) continue
            val fundA = a / n1
            val fundB = b / n2
            if (fundA < minFreq - 8.0 || fundB < minFreq - 8.0) continue
            val cents = 1200.0 * kotlin.math.abs(Math.log(fundA / fundB) / Math.log(2.0))
            if (cents > 35.0) continue
            val fund = (fundA + fundB) / 2.0
            if (magAt(spec, fund) >= maxMag * 0.12) return true
        }
    }
    return false
}

internal fun resolveOctave(yinHz: Double, hpsHz: Double?, spec: Spectrum?): Double {
    var chosen = yinHz
    if (hpsHz != null && hpsHz.isFinite() && yinHz > 0) {
        val ratio = yinHz / hpsHz
        chosen = when {
            ratio in 0.94..1.06 -> yinHz
            ratio in 1.87..2.14 -> {
                if (spec == null) {
                    hpsHz
                } else {
                    val low = magAt(spec, hpsHz)
                    val high = magAt(spec, yinHz)
                    if (high <= 0 || low >= high * 0.22) hpsHz else yinHz
                }
            }
            else -> yinHz
        }
    }
    return if (spec == null) chosen else foldSubharmonics(chosen, spec)
}

/**
 * YIN and HPS often agree on 2f when a piano's 2nd partial is louder than
 * the fundamental (C4 read as C5). Fold down while a true subharmonic bin
 * still has energy. A pure sine has none, so A4 stays A4.
 */
internal fun foldSubharmonics(hz: Double, spec: Spectrum): Double {
    val high = magAt(spec, hz)
    if (high <= 0.0) return hz
    var best = hz
    for (div in 2..4) {
        val lower = hz / div
        if (lower < YinHpsPitchDetector.MIN_FREQ_HZ) continue
        val low = magAt(spec, lower)
        if (low >= high * 0.18) best = lower
    }
    return best
}

private fun magAt(spec: Spectrum, freq: Double): Double {
    val bin = Math.round(freq * spec.n / spec.sampleRate).toInt()
    if (bin <= 0 || bin >= spec.mag.size) return 0.0
    var best = spec.mag[bin]
    if (bin - 1 > 0) best = maxOf(best, spec.mag[bin - 1])
    if (bin + 1 < spec.mag.size) best = maxOf(best, spec.mag[bin + 1])
    return best
}

private fun nextPow2(n: Int): Int {
    var p = 1
    while (p < n) p *= 2
    return p
}

private fun fft(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    var j = 0
    for (i in 0 until n) {
        if (i < j) {
            val tr = real[i]; real[i] = real[j]; real[j] = tr
            val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
        }
        var m = n shr 1
        while (m >= 1 && j >= m) {
            j -= m
            m = m shr 1
        }
        j += m
    }
    var size = 2
    while (size <= n) {
        val half = size shr 1
        val step = 2 * Math.PI / size
        var i = 0
        while (i < n) {
            for (k in 0 until half) {
                val angle = step * k
                val wr = Math.cos(angle)
                val wi = -Math.sin(angle)
                val evenR = real[i + k]
                val evenI = imag[i + k]
                val oddR = real[i + k + half]
                val oddI = imag[i + k + half]
                val tr = wr * oddR - wi * oddI
                val ti = wr * oddI + wi * oddR
                real[i + k] = evenR + tr
                imag[i + k] = evenI + ti
                real[i + k + half] = evenR - tr
                imag[i + k + half] = evenI - ti
            }
            i += size
        }
        size = size shl 1
    }
}
