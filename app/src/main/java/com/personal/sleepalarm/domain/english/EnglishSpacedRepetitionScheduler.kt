package com.personal.sleepalarm.domain.english

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Compact SM-2 inspired scheduler. All values are deterministic and stored in
 * minutes so the first learning steps do not get rounded to whole days.
 */
object EnglishSpacedRepetitionScheduler {
    const val DEFAULT_EASE_PERMILLE = 2_500
    const val MIN_EASE_PERMILLE = 1_300

    private const val MINUTE_MILLIS = 60_000L
    private const val TEN_MINUTES = 10L
    private const val SIX_HOURS = 6L * 60L
    private const val ONE_DAY = 24L * 60L
    private const val THREE_DAYS = 3L * ONE_DAY
    private const val FOUR_DAYS = 4L * ONE_DAY
    private const val MAX_INTERVAL = 365L * ONE_DAY

    fun schedule(
        previous: EnglishReviewState?,
        grade: EnglishReviewGrade,
        nowMillis: Long
    ): EnglishReviewSchedule {
        val state = previous ?: EnglishReviewState()
        val nextEase = when (grade) {
            EnglishReviewGrade.AGAIN -> max(MIN_EASE_PERMILLE, state.easePermille - 200)
            EnglishReviewGrade.HARD -> max(MIN_EASE_PERMILLE, state.easePermille - 150)
            EnglishReviewGrade.GOOD -> state.easePermille
            EnglishReviewGrade.EASY -> (state.easePermille + 150).coerceAtMost(3_500)
        }
        val interval = when (grade) {
            EnglishReviewGrade.AGAIN -> TEN_MINUTES
            EnglishReviewGrade.HARD -> if (state.repetitions == 0) {
                SIX_HOURS
            } else {
                max(ONE_DAY, (state.intervalMinutes * 1.2).roundToLong())
            }
            EnglishReviewGrade.GOOD -> when (state.repetitions) {
                0 -> ONE_DAY
                1 -> THREE_DAYS
                else -> max(
                    ONE_DAY,
                    (state.intervalMinutes * nextEase / 1_000.0).roundToLong()
                )
            }
            EnglishReviewGrade.EASY -> if (state.repetitions == 0) {
                FOUR_DAYS
            } else {
                max(
                    FOUR_DAYS,
                    (state.intervalMinutes * nextEase / 1_000.0 * 1.3).roundToLong()
                )
            }
        }.coerceAtMost(MAX_INTERVAL)

        return EnglishReviewSchedule(
            intervalMinutes = interval,
            dueAtMillis = nowMillis + interval * MINUTE_MILLIS,
            easePermille = nextEase,
            repetitions = if (grade == EnglishReviewGrade.AGAIN) 0 else state.repetitions + 1,
            lapses = state.lapses + if (grade == EnglishReviewGrade.AGAIN) 1 else 0
        )
    }
}
