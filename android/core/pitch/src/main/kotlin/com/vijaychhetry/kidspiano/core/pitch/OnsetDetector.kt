package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.Config
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-jump onset on every hop (spec v4 §4.3). Independent of pitch,
 * so a re-press of the same decaying key is still a new press.
 */
class OnsetDetector(
    private val riseDb: Double = Config.ONSET_RISE_DB,
    private val minAboveFloorDb: Double = Config.ONSET_MIN_ABOVE_FLOOR_DB,
    private val minGapMs: Int = Config.MIN_ONSET_GAP_MS,
) {
    private val recent = ArrayDeque<Double>()
    private var floorDb = -80.0
    private var lastOnsetMs = -1_000_000L
    var lastRmsDb: Double = -80.0
        private set
    var lastFloorDb: Double = -80.0
        private set

    data class Result(val onset: Boolean, val rmsDb: Double, val floorDb: Double)

    fun onHop(samples: FloatArray, nowMs: Long): Result {
        val rms = rms(samples)
        val db = if (rms <= 1e-9) -90.0 else 20.0 * log10(rms)
        lastRmsDb = db
        recent.addLast(db)
        if (recent.size > 8) recent.removeFirst()

        val quiet = db < floorDb + 6.0
        if (quiet) {
            floorDb = if (db < floorDb) db else floorDb * 0.98 + db * 0.02
        } else {
            floorDb += 0.01
        }
        lastFloorDb = floorDb

        val twoBack = if (recent.size >= 3) recent.elementAt(recent.size - 3) else db
        val rise = db - twoBack
        val loudEnough = db >= floorDb + minAboveFloorDb
        val rising = rise >= riseDb || recent.size < 3
        val onset = loudEnough &&
            rising &&
            nowMs - lastOnsetMs >= minGapMs
        if (onset) lastOnsetMs = nowMs
        return Result(onset, db, floorDb)
    }

    fun reset() {
        recent.clear()
        floorDb = -80.0
        lastOnsetMs = -1_000_000L
        lastRmsDb = -80.0
        lastFloorDb = -80.0
    }

    companion object {
        fun rms(samples: FloatArray): Double {
            var s = 0.0
            for (x in samples) s += x * x
            return sqrt(s / max(1, samples.size))
        }
    }
}
