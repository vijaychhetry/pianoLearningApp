package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult

interface PitchDetector {
    fun detect(frame: AudioFrame): PitchResult
}

data class YinEstimate(val frequency: Double?, val probability: Double)

fun rms(samples: FloatArray): Double {
    var sum = 0.0
    for (s in samples) sum += s * s
    return Math.sqrt(sum / samples.size)
}

fun detectPitchYin(
    samples: FloatArray,
    sampleRate: Int,
    threshold: Double = 0.12,
    minFreq: Double = 130.0,
    maxFreq: Double = 800.0,
): YinEstimate {
    val half = samples.size / 2
    val tauMin = maxOf(2, Math.floor(sampleRate / maxFreq).toInt())
    val tauMax = minOf(half - 1, Math.floor(sampleRate / minFreq).toInt())
    if (tauMax <= tauMin + 2) return YinEstimate(null, 0.0)

    val yin = DoubleArray(tauMax + 1)
    for (tau in 1..tauMax) {
        var sum = 0.0
        for (i in 0 until half) {
            val delta = samples[i] - samples[i + tau]
            sum += delta * delta
        }
        yin[tau] = sum
    }
    yin[0] = 1.0
    var running = 0.0
    for (tau in 1..tauMax) {
        running += yin[tau]
        yin[tau] = if (running == 0.0) 1.0 else (yin[tau] * tau) / running
    }

    var tauEstimate = -1
    var tau = tauMin
    while (tau <= tauMax) {
        if (yin[tau] < threshold) {
            while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) tau++
            tauEstimate = tau
            break
        }
        tau++
    }
    if (tauEstimate == -1) {
        var best = 1.0
        var bestTau = -1
        for (t in tauMin..tauMax) {
            if (yin[t] < best) {
                best = yin[t]
                bestTau = t
            }
        }
        if (bestTau == -1 || best > 0.35) return YinEstimate(null, 0.0)
        tauEstimate = bestTau
    }

    val betterTau = parabolic(yin, tauEstimate)
    if (betterTau <= 0) return YinEstimate(null, 0.0)
    return YinEstimate(
        frequency = sampleRate / betterTau,
        probability = (1.0 - yin[tauEstimate]).coerceIn(0.0, 1.0),
    )
}

private fun parabolic(buffer: DoubleArray, tau: Int): Double {
    val x0 = if (tau < 1) tau else tau - 1
    val x2 = if (tau + 1 < buffer.size) tau + 1 else tau
    if (x0 == tau || x2 == tau) return tau.toDouble()
    val s0 = buffer[x0]
    val s1 = buffer[tau]
    val s2 = buffer[x2]
    val denom = 2 * s1 - s2 - s0
    if (denom == 0.0) return tau.toDouble()
    return tau + (s2 - s0) / (2 * denom)
}
