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
 * Captures PCM from the device mic. Tries UNPROCESSED, then VOICE_RECOGNITION, then MIC.
 * Bounded SharedFlow drops oldest frames under backpressure (spec §3).
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

    @Synchronized
    override fun start() {
        if (running) return
        val started = openRecorder()
        record = started
        running = true
        worker = thread(name = "piano-audio", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val shortBuf = ShortArray(frameSize)
            val floatBuf = FloatArray(frameSize)
            val rec = record ?: return@thread
            rec.startRecording()
            while (running) {
                val read = rec.read(shortBuf, 0, shortBuf.size)
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
                        sampleRate = appliedSampleRate,
                        capturedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    @Synchronized
    override fun stop() {
        running = false
        worker?.join(500)
        worker = null
        record?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        record = null
    }

    private fun openRecorder(): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
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
        throw IllegalStateException("Could not open AudioRecord", lastError)
    }
}
