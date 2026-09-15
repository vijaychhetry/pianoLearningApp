package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.notes.A4_HZ
import com.vijaychhetry.kidspiano.core.notes.MAX_PIANO_MIDI
import com.vijaychhetry.kidspiano.core.notes.MIN_PIANO_MIDI
import com.vijaychhetry.kidspiano.core.notes.freqToMidi

class YinHpsPitchDetector(
    private val minFreq: Double = 130.0,
    private val maxFreq: Double = 800.0,
    private val probabilityThreshold: Double = 0.55,
    private val minRms: Double = 0.008,
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
        val midi = if (freq != null) freqToMidi(freq, A4_HZ) else null
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
}
