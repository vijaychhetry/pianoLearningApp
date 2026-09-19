package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.Config
import com.vijaychhetry.kidspiano.core.common.OctaveEvidence
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.PressEvent
import com.vijaychhetry.kidspiano.core.common.PressKind
import com.vijaychhetry.kidspiano.core.notes.centsOff
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import kotlin.math.log10

/**
 * One onset → one [PressEvent] (spec v4 §4.4). Pitch is ignored after the
 * event is emitted until the next onset or a release.
 */
class PressTracker(
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val onset: OnsetDetector = OnsetDetector(),
    private val agreeFrames: Int = Config.AGREE_FRAMES,
    private val timeoutMs: Int = Config.PRESS_TIMEOUT_MS,
    private val minClarity: Double = 0.55,
    private val releaseBelowPeakDb: Double = Config.RELEASE_BELOW_PEAK_DB,
    private val minLevelAboveFloorDb: Double = Config.MIN_LEVEL_DB_ABOVE_FLOOR,
) {
    private enum class Phase { IDLE, ATTACK, HELD }

    private var phase = Phase.IDLE
    private var onsetNanos = 0L
    private var peakDb = -90.0
    private var agreeCount = 0
    private var candidateMidi: Int? = null
    private var candidateHz: Double? = null
    private var candidateConf = 0.0
    private var candidateClarity = 0.0
    private var emitted = false
    private var lastPitch: PitchResult? = null

    fun lastPitch(): PitchResult? = lastPitch
    fun lastLevelDb(): Double = onset.lastRmsDb
    fun lastFloorDb(): Double = onset.lastFloorDb

    fun onFrame(frame: AudioFrame): PressEvent? {
        val hop = onset.onHop(frame.samples, frame.capturedAtMs)
        val pitch = detector.detect(frame)
        lastPitch = pitch
        val nowNanos = frame.capturedAtMs * 1_000_000L

        if (hop.onset && phase == Phase.IDLE) {
            startAttack(nowNanos, hop.rmsDb)
        }

        return when (phase) {
            Phase.IDLE -> null
            Phase.ATTACK -> onAttack(frame, hop, pitch, nowNanos)
            Phase.HELD -> {
                if (hop.rmsDb <= peakDb - releaseBelowPeakDb) resetToIdle()
                null
            }
        }
    }

    fun reset() {
        onset.reset()
        resetToIdle()
        lastPitch = null
    }

    private fun startAttack(nowNanos: Long, rmsDb: Double) {
        phase = Phase.ATTACK
        onsetNanos = nowNanos
        peakDb = rmsDb
        agreeCount = 0
        candidateMidi = null
        candidateHz = null
        candidateConf = 0.0
        candidateClarity = 0.0
        emitted = false
    }

    private fun onAttack(
        frame: AudioFrame,
        hop: OnsetDetector.Result,
        pitch: PitchResult,
        nowNanos: Long,
    ): PressEvent? {
        peakDb = maxOf(peakDb, hop.rmsDb)
        val elapsedMs = (nowNanos - onsetNanos) / 1_000_000L
        if (hop.rmsDb <= peakDb - releaseBelowPeakDb && elapsedMs >= 20) {
            val kind = if (peakDb < hop.floorDb + minLevelAboveFloorDb) {
                PressKind.TOO_QUIET
            } else {
                PressKind.TOO_SHORT
            }
            return emit(kind, frame, nowNanos, pitch, hop)
        }
        if (elapsedMs >= timeoutMs) {
            val kind = when {
                peakDb < hop.floorDb + minLevelAboveFloorDb -> PressKind.TOO_QUIET
                pitch.ambiguous -> PressKind.TWO_NOTES
                else -> PressKind.LOW_CONFIDENCE
            }
            return emit(kind, frame, nowNanos, pitch, hop)
        }
        if (elapsedMs < Config.ATTACK_SKIP_MS) return null
        if (pitch.ambiguous) {
            return emit(PressKind.TWO_NOTES, frame, nowNanos, pitch, hop)
        }
        val midi = pitch.midiNote
        val hz = pitch.frequency
        if (midi == null || hz == null || pitch.clarity < minClarity) return null
        if (candidateMidi == midi) {
            agreeCount++
        } else {
            candidateMidi = midi
            candidateHz = hz
            candidateConf = pitch.confidence
            candidateClarity = pitch.clarity
            agreeCount = 1
        }
        if (agreeCount >= agreeFrames) {
            return emit(PressKind.NOTE, frame, nowNanos, pitch, hop)
        }
        return null
    }

    private fun emit(
        kind: PressKind,
        frame: AudioFrame,
        nowNanos: Long,
        pitch: PitchResult,
        hop: OnsetDetector.Result,
    ): PressEvent {
        emitted = true
        phase = if (kind == PressKind.NOTE) Phase.HELD else Phase.IDLE
        val midi = if (kind == PressKind.NOTE) candidateMidi ?: pitch.midiNote else pitch.midiNote
        val hz = if (kind == PressKind.NOTE) candidateHz ?: pitch.frequency else pitch.frequency
        val cents = if (hz != null && midi != null) centsOff(hz, midi) else null
        return PressEvent(
            onsetNanos = onsetNanos,
            verdictNanos = nowNanos,
            kind = kind,
            hz = hz,
            midi = midi,
            centsOff = cents,
            confidence = if (kind == PressKind.NOTE) candidateConf else pitch.confidence,
            clarity = if (kind == PressKind.NOTE) candidateClarity else pitch.clarity,
            levelDb = hop.rmsDb,
            octaveEvidence = midi?.let { evidenceFor(frame.samples, frame.sampleRate, it) },
        )
    }

    /**
     * Narrow-bin energy at the detected fundamental and at one octave below.
     * Judge uses this to decide CORRECT vs WRONG_OCTAVE vs OCTAVE_UNSURE.
     */
    private fun evidenceFor(
        samples: FloatArray,
        sampleRate: Int,
        detectedMidi: Int,
    ): OctaveEvidence = OctaveEvidence(
        expectedFundamentalDb = goertzelDb(samples, sampleRate, midiToFreq(detectedMidi - 12)),
        detectedFundamentalDb = goertzelDb(samples, sampleRate, midiToFreq(detectedMidi)),
    )

    private fun resetToIdle() {
        phase = Phase.IDLE
        agreeCount = 0
        candidateMidi = null
        emitted = false
    }
}

fun goertzelDb(samples: FloatArray, sampleRate: Int, targetHz: Double): Double {
    if (targetHz <= 0.0 || samples.isEmpty()) return -90.0
    val k = (0.5 + samples.size * targetHz / sampleRate).toInt()
    val w = 2.0 * Math.PI * k / samples.size
    val cosine = Math.cos(w)
    val coeff = 2.0 * cosine
    var s0 = 0.0
    var s1 = 0.0
    var s2 = 0.0
    for (x in samples) {
        s0 = x + coeff * s1 - s2
        s2 = s1
        s1 = s0
    }
    val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
    return 10.0 * log10(maxOf(power, 1e-12))
}

fun octaveEvidenceFromSamples(
    samples: FloatArray,
    sampleRate: Int,
    expectedMidi: Int,
    detectedMidi: Int,
): OctaveEvidence {
    val eHz = midiToFreq(expectedMidi)
    val dHz = midiToFreq(detectedMidi)
    return OctaveEvidence(
        expectedFundamentalDb = goertzelDb(samples, sampleRate, eHz),
        detectedFundamentalDb = goertzelDb(samples, sampleRate, dHz),
    )
}
