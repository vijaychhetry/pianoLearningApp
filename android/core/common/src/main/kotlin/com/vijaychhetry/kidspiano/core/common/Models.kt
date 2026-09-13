package com.vijaychhetry.kidspiano.core.common

enum class RecognitionStatus {
    NO_SIGNAL,
    LISTENING,
    NOTE_DETECTED,
    HIGH_CONFIDENCE,
    LOW_CONFIDENCE,
    AMBIGUOUS,
    CORRECT,
    INCORRECT,
    UNCLEAR,
    CORRECT_OCTAVE_MISMATCH,
}

data class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    val capturedAtMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * sampleRate + samples.contentHashCode()
}

data class PitchResult(
    val frequency: Double?,
    val midiNote: Int?,
    val confidence: Double,
    val clarity: Double,
    val signalStrength: Double,
    val stability: Double,
    val timestampMs: Long,
    val latencyMs: Long,
    val yinHz: Double? = null,
    val hpsHz: Double? = null,
)

data class RecognizedNote(
    val midi: Int,
    val name: String,
    val frequency: Double,
    val confidence: Double,
)

data class ValidationResult(
    val status: RecognitionStatus,
    val expectedNote: Int?,
    val detectedNote: Int?,
    val confidence: Double,
    val message: String,
)

interface AudioInput {
    fun start()
    fun stop()
    fun audioFrames(): kotlinx.coroutines.flow.Flow<AudioFrame>
}
