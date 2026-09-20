package com.vijaychhetry.kidspiano.core.common

/**
 * Every number the spec marks (tunable), in one place (spec v4 §4.1).
 * Logic reads these; it does not invent a second set of magic numbers.
 */
object Config {
    val preferredRates: List<Int> = listOf(48_000, 44_100)
    const val WINDOW = 2048
    const val HOP = 512

    const val ONSET_RISE_DB = 6.0
    const val ONSET_MIN_ABOVE_FLOOR_DB = 12.0
    const val MIN_ONSET_GAP_MS = 150
    const val ATTACK_SKIP_MS = 60

    const val F_MIN = 120.0
    const val F_MAX = 1100.0
    const val MIN_CLARITY = 0.55
    const val MIN_CONFIDENCE = 0.70
    const val AGREE_FRAMES = 3
    const val CLASSIFY_MAX_CENTS = 50.0
    const val TWO_NOTE_SALIENCE_DB = 10.0
    const val OCTAVE_FUNDAMENTAL_DB = 6.0
    const val OCTAVE_RESCUE_DB = 18.0
    const val OCTAVE_WRONG_DB = 28.0

    const val RELEASE_BELOW_PEAK_DB = 20.0
    const val PRESS_TIMEOUT_MS = 350
    const val MIN_LEVEL_DB_ABOVE_FLOOR = 10.0

    const val HINT_IDLE_MS = 4_000L
    const val HINT_SILENCE_AFTER_WRONG_MS = 12_000L
    const val PROMPT_NUDGE_MS = 20_000L
    const val MIC_RELEASE_MS = 60_000L

    const val COMFORT_FIRST_TRY = 0.80
    const val COMFORT_MIN_SAMPLES = 6

    const val GATE_HOLD_MS = 2_000L
    const val GATE_FACTOR_MIN = 6
    const val GATE_FACTOR_MAX = 9

    const val FALL_MS = 3_000L
    const val SPAWN_GAP_MS = 2_500L
    const val EARLY_MS = 450L
    const val LATE_MS = 900L
    const val TILE_COUNT = 12
}
