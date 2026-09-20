package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.common.Config
import kotlin.random.Random

/**
 * Times-table speed bump (spec v4 §9.9). Factors in [6, 9], re-rolled
 * every visit. Not security — a four-year-old cannot mash through it.
 */
data class ParentGate(
    val a: Int = Random.nextInt(Config.GATE_FACTOR_MIN, Config.GATE_FACTOR_MAX + 1),
    val b: Int = Random.nextInt(Config.GATE_FACTOR_MIN, Config.GATE_FACTOR_MAX + 1),
) {
    init {
        require(a in Config.GATE_FACTOR_MIN..Config.GATE_FACTOR_MAX)
        require(b in Config.GATE_FACTOR_MIN..Config.GATE_FACTOR_MAX)
    }

    val prompt: String get() = "What is $a × $b?"

    fun accepts(answer: String): Boolean = answer.trim().toIntOrNull() == a * b

    companion object {
        fun of(a: Int, b: Int): ParentGate = ParentGate(a, b)
    }
}
