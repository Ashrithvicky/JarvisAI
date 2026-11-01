package com.privateai.voice

import com.privateai.memory.Memory
import com.privateai.memory.MemoryType
import kotlin.random.Random

/** Pure helpers for choosing when and what VIVVA says unprompted. */
object ProactiveNudges {
    const val MIN_INTERVAL_MIN = 45L
    const val MAX_INTERVAL_MIN = 120L
    const val QUIET_START_HOUR = 22
    const val QUIET_END_HOUR = 8

    fun nextDelayMillis(random: Random = Random.Default): Long =
        random.nextLong(MIN_INTERVAL_MIN, MAX_INTERVAL_MIN + 1) * 60_000L

    fun isQuietHour(hour: Int): Boolean = hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR

    fun compose(memories: List<Memory>, random: Random = Random.Default): String {
        val goals = memories.filter { it.type == MemoryType.GOAL }
        if (goals.isNotEmpty()) {
            val goal = goals[random.nextInt(goals.size)].content.trim().trimEnd('.')
            return "Hey boss, quick check. Is what you're doing right now moving you toward this: $goal?"
        }
        return GENERIC[random.nextInt(GENERIC.size)]
    }

    private val GENERIC = listOf(
        "Hey boss, I'm here if you need anything.",
        "Boss, have you taken a break recently? Stretch for a minute.",
        "Hey boss, want to plan the next thing you're working on?"
    )
}
