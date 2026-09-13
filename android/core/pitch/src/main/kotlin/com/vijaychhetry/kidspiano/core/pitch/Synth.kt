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
