package com.personal.sleepalarm.domain.english

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

object EnglishAnswerEvaluator {
    fun evaluateTyped(expected: String, actual: String): EnglishAnswerEvaluation {
        val normalizedExpected = normalize(expected)
        val normalizedActual = normalize(actual)
        if (normalizedExpected == normalizedActual) {
            return result(
                grade = EnglishReviewGrade.GOOD,
                correct = true,
                expected = normalizedExpected,
                actual = normalizedActual,
                feedback = EnglishAnswerFeedback.CORRECT
            )
        }
        val minorTypo = normalizedExpected.length >= 5 && (
            editDistance(normalizedExpected, normalizedActual) == 1 ||
                hasSingleAdjacentTransposition(normalizedExpected, normalizedActual)
            )
        return result(
            grade = if (minorTypo) EnglishReviewGrade.HARD else EnglishReviewGrade.AGAIN,
            correct = minorTypo,
            expected = normalizedExpected,
            actual = normalizedActual,
            feedback = if (minorTypo) EnglishAnswerFeedback.MINOR_TYPO else EnglishAnswerFeedback.INCORRECT
        )
    }

    fun evaluateSpeech(expected: String, hypotheses: List<String>): EnglishAnswerEvaluation {
        val normalizedExpected = normalize(expected)
        val matches = hypotheses
            .flatMap { hypothesis ->
                val normalized = normalize(hypothesis)
                listOf(normalized) + normalized.split(' ')
            }
        val exact = matches.any { it == normalizedExpected }
        return result(
            grade = if (exact) EnglishReviewGrade.GOOD else EnglishReviewGrade.AGAIN,
            correct = exact,
            expected = normalizedExpected,
            actual = hypotheses.firstOrNull()?.let(::normalize).orEmpty(),
            feedback = if (exact) EnglishAnswerFeedback.SPEECH_CORRECT else EnglishAnswerFeedback.SPEECH_INCORRECT
        )
    }

    fun normalize(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
            .lowercase(Locale.US)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .replace(Regex("[^a-z']+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    internal fun editDistance(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = min(
                    min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                    previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    /** Treats a single swapped neighbouring pair as one ordinary typing slip. */
    internal fun hasSingleAdjacentTransposition(left: String, right: String): Boolean {
        if (left.length != right.length || left.length < 2) return false
        val mismatches = left.indices.filter { left[it] != right[it] }
        if (mismatches.size != 2 || mismatches[1] != mismatches[0] + 1) return false
        val first = mismatches[0]
        val second = mismatches[1]
        return left[first] == right[second] && left[second] == right[first]
    }

    private fun result(
        grade: EnglishReviewGrade,
        correct: Boolean,
        expected: String,
        actual: String,
        feedback: EnglishAnswerFeedback
    ) = EnglishAnswerEvaluation(
        grade = grade,
        isCorrect = correct,
        normalizedExpected = expected,
        normalizedActual = actual,
        feedback = feedback
    )
}
