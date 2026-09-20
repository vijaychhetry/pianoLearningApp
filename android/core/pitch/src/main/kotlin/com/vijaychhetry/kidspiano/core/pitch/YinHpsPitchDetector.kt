package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.notes.A4_HZ
import com.vijaychhetry.kidspiano.core.notes.MAX_PIANO_MIDI
import com.vijaychhetry.kidspiano.core.notes.MIN_PIANO_MIDI
import com.vijaychhetry.kidspiano.core.notes.freqToMidi

/**
 * Configured range is A2–F6 (110–1400 Hz), tested to C6, so a child hunting
 * for keys outside the five-note MVP sees a reading instead of a dead screen.
 */
class YinHpsPitchDetector(
    private val minFreq: Double = MIN_FREQ_HZ,
    private val maxFreq: Double = MAX_FREQ_HZ,
    private val probabilityThreshold: Double = 0.55,
    private val minRms: Double = MIN_RMS,
    private val a4Hz: Double = A4_HZ,
) : PitchDetector {
    override fun detect(frame: AudioFrame): PitchResult {
        val started = System.nanoTime()
        val level = rms(frame.samples)
        if (level < minRms) {
            return PitchResult(
                frequency = null,
                midiNote = null,
                confidence = 0.0,
                clarity = 0.0,
                signalStrength = level,
                stability = 0.0,
                timestampMs = frame.capturedAtMs,
                latencyMs = elapsedMs(started),
            )
        }
        val yin = detectPitchYin(frame.samples, frame.sampleRate, minFreq = minFreq, maxFreq = maxFreq)
        val spec = computeSpectrum(frame.samples, frame.sampleRate)
        val hps = detectPitchHps(spec, minFreq, maxFreq, 4)
        val polyphonic = isPolyphonic(spec, minFreq, maxFreq)
        val freq = if (yin.frequency != null && yin.probability >= probabilityThreshold) {
            resolveOctave(yin.frequency, hps, spec)
        } else {
            null
        }
        val midi = if (freq != null) freqToMidi(freq, a4Hz) else null
        val inRange = midi != null && midi in MIN_PIANO_MIDI..MAX_PIANO_MIDI
        return PitchResult(
            frequency = if (inRange) freq else null,
            midiNote = if (inRange) midi else null,
            confidence = if (inRange) yin.probability else 0.0,
            clarity = yin.probability,
            signalStrength = level,
            stability = if (inRange) yin.probability else 0.0,
            timestampMs = frame.capturedAtMs,
            latencyMs = elapsedMs(started),
            yinHz = yin.frequency,
            hpsHz = hps,
            ambiguous = polyphonic,
        )
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    companion object {
        const val MIN_FREQ_HZ = 110.0
        const val MAX_FREQ_HZ = 1400.0

        /** Below this the frame is treated as room noise, not a key press. */
        const val MIN_RMS = 0.005
    }
}
