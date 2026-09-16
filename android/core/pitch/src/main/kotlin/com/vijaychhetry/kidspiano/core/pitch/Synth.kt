package com.vijaychhetry.kidspiano.core.pitch

fun pianoTone(
    frequency: Double,
    sampleRate: Int,
    durationSec: Double,
    amplitude: Float = 0.35f,
    harmonics: Int = 6,
): FloatArray {
    val n = (sampleRate * durationSec).toInt()
    val out = FloatArray(n)
    val weight = (1..harmonics).sumOf { 1.0 / it }
    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        var sample = 0.0
        for (h in 1..harmonics) {
            val w = 1.0 / h
            sample += w * Math.sin(2 * Math.PI * frequency * h * t) * Math.exp(-2.2 * h * 0.15 * t)
        }
        val attack = minOf(1.0, t / 0.008)
        out[i] = (amplitude * attack * (sample / weight)).toFloat()
    }
    return out
}

/**
 * Closer to a miked piano than [pianoTone]: stretched (inharmonic) partials,
 * a hammer noise burst, and mains hum from the room.
 */
fun realisticPianoTone(
    frequency: Double,
    sampleRate: Int,
    durationSec: Double,
    amplitude: Float = 0.3f,
    partials: Int = 8,
    inharmonicity: Double = 0.0004,
    noiseLevel: Double = 0.02,
    humLevel: Double = 0.01,
    seed: Long = 42L,
): FloatArray {
    val n = (sampleRate * durationSec).toInt()
    val out = FloatArray(n)
    val random = java.util.Random(seed)
    val weight = (1..partials).sumOf { 1.0 / Math.pow(it.toDouble(), 1.2) }
    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        var sample = 0.0
        for (h in 1..partials) {
            val stretched = h * frequency * Math.sqrt(1.0 + inharmonicity * h * h)
            if (stretched >= sampleRate / 2.0) continue
            val w = 1.0 / Math.pow(h.toDouble(), 1.2)
            sample += w * Math.sin(2 * Math.PI * stretched * t) * Math.exp(-0.9 * h * 0.18 * t)
        }
        sample /= weight
        val hammer = random.nextGaussian() * noiseLevel * Math.exp(-t / 0.02)
        val hum = humLevel * Math.sin(2 * Math.PI * 50.0 * t)
        val attack = minOf(1.0, t / 0.006)
        out[i] = (amplitude * attack * sample + hammer + hum).toFloat()
    }
    return out
}

fun mixTones(a: FloatArray, b: FloatArray): FloatArray {
    val n = minOf(a.size, b.size)
    val out = FloatArray(n)
    for (i in 0 until n) out[i] = a[i] + b[i]
    return out
}

fun sineTone(frequency: Double, sampleRate: Int, durationSec: Double, amplitude: Float = 0.4f): FloatArray {
    val n = (sampleRate * durationSec).toInt()
    val out = FloatArray(n)
    for (i in 0 until n) {
        out[i] = (amplitude * Math.sin(2 * Math.PI * frequency * i / sampleRate)).toFloat()
    }
    return out
}
