package com.vijaychhetry.kidspiano.core.pitch

import kotlin.math.log10

/**
 * Maps an RMS level to a 0..1 bar. Log-scaled over 60 dB so room noise and a
 * struck key look different; a linear bar sits near zero for both.
 */
fun levelFraction(rms: Double): Float {
    if (!rms.isFinite() || rms <= FLOOR) return 0f
    val db = 20.0 * log10(rms)
    return ((db + RANGE_DB) / RANGE_DB).coerceIn(0.0, 1.0).toFloat()
}

private const val FLOOR = 0.00001
private const val RANGE_DB = 60.0
