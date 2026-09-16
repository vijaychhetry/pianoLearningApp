package com.vijaychhetry.kidspiano.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.AudioInput
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.concurrent.thread

/**
 * Captures PCM from the device mic. Tries UNPROCESSED, then VOICE_RECOGNITION,
 * then MIC. Bounded SharedFlow drops oldest frames under backpressure (spec §3).
 *
 * `STATE_INITIALIZED` does not prove a source works — some phones accept
 * UNPROCESSED and return silence — so the caller can pin a specific source and
 * retry. See `SilenceWatchdog`.
 *
 * The capture thread owns its `AudioRecord` for its whole life and is the only
 * thread that releases it. `AudioRecord.stop()` is safe to call from another
 * thread and unblocks a pending `read()`; `release()` is not, and calling it
 * under a blocked reader is a native use-after-free.
 *
 * [start] and [stop] can block for up to half a second, so call them off the
 * main thread.
 */
class AudioRecordInput(
    private val requestedSampleRate: Int = 44100,
    private val frameSize: Int = 2048,
) : AudioInput {
    private val frames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    var appliedSource: Int = MediaRecorder.AudioSource.MIC
        private set
    var appliedSampleRate: Int = requestedSampleRate
        private set

    override fun audioFrames(): Flow<AudioFrame> = frames

    override fun start() = start(null)

    /** @param preferredSource pin one `MediaRecorder.AudioSource`, or null to try each in order. */
    @Synchronized
    fun start(preferredSource: Int?) {
        if (running) return
        // Let the previous capture release its recorder; some devices refuse a
        // second concurrent AudioRecord.
        worker?.join(WORKER_EXIT_TIMEOUT_MS)
        worker = null
        val started = openRecorder(preferredSource)
        started.startRecording()
        if (started.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            started.release()
            throw IllegalStateException("Microphone did not start (source ${sourceName(appliedSource)})")
        }
        record = started
        running = true
        val rate = appliedSampleRate
        worker = thread(name = "piano-audio", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val shortBuf = ShortArray(frameSize)
            val floatBuf = FloatArray(frameSize)
            try {
                while (running) {
                    val read = started.read(shortBuf, 0, shortBuf.size)
                    if (read <= 0) continue
                    for (i in 0 until read) {
                        floatBuf[i] = shortBuf[i] / 32768f
                    }
                    if (read < frameSize) {
                        for (i in read until frameSize) floatBuf[i] = 0f
                    }
                    frames.tryEmit(
                        AudioFrame(
                            samples = floatBuf.copyOf(),
                            sampleRate = rate,
                            capturedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                try {
                    started.stop()
                } catch (_: IllegalStateException) {
                }
                started.release()
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!running) return
        running = false
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
        }
        record = null
    }

    private fun openRecorder(preferredSource: Int?): AudioRecord {
        val sources = if (preferredSource != null) listOf(preferredSource) else SOURCE_ORDER
        val min = AudioRecord.getMinBufferSize(
            requestedSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val buf = maxOf(min * 2, frameSize * 4)
        var lastError: Exception? = null
        for (source in sources) {
            try {
                val rec = AudioRecord(
                    source,
                    requestedSampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buf,
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    appliedSource = source
                    appliedSampleRate = rec.sampleRate
                    return rec
                }
                rec.release()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("Could not open the microphone", lastError)
    }

    companion object {
        private const val WORKER_EXIT_TIMEOUT_MS = 500L

        val SOURCE_ORDER: List<Int> = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        fun sourceName(source: Int): String = when (source) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            else -> "source $source"
        }
    }
}
