package com.personal.sleepalarm.domain.english

enum class EnglishLearningMode {
    CARDS,
    WRITING,
    PRONUNCIATION,
    LISTENING
}

enum class EnglishReviewGrade {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

data class EnglishReviewState(
    val intervalMinutes: Long = 0L,
    val easePermille: Int = EnglishSpacedRepetitionScheduler.DEFAULT_EASE_PERMILLE,
    val repetitions: Int = 0,
    val lapses: Int = 0
)

data class EnglishReviewSchedule(
    val intervalMinutes: Long,
    val dueAtMillis: Long,
    val easePermille: Int,
    val repetitions: Int,
    val lapses: Int
)

data class EnglishAnswerEvaluation(
    val grade: EnglishReviewGrade,
    val isCorrect: Boolean,
    val normalizedExpected: String,
    val normalizedActual: String,
    val feedback: EnglishAnswerFeedback
)

enum class EnglishAnswerFeedback {
    CORRECT,
    MINOR_TYPO,
    INCORRECT,
    SPEECH_CORRECT,
    SPEECH_INCORRECT,
    SELF_REPORTED_CORRECT,
    TRY_AGAIN,
    CARD_SAVED
}
