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

internal fun resolveOctave(yinHz: Double, hpsHz: Double?, spec: Spectrum?): Double {
    if (hpsHz == null || !hpsHz.isFinite() || yinHz <= 0) return yinHz
    val ratio = yinHz / hpsHz
    if (ratio in 0.94..1.06) return yinHz
    val octaveHigh = ratio in 1.87..2.14
    if (!octaveHigh) return yinHz
    if (spec == null) return hpsHz
    val low = magAt(spec, hpsHz)
    val high = magAt(spec, yinHz)
    if (high <= 0) return hpsHz
    return if (low >= high * 0.22) hpsHz else yinHz
}

private fun magAt(spec: Spectrum, freq: Double): Double {
    val bin = Math.round(freq * spec.n / spec.sampleRate).toInt()
    if (bin <= 0 || bin >= spec.mag.size) return 0.0
    return spec.mag[bin]
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
