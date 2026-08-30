package com.personal.sleepalarm.util

import com.personal.sleepalarm.domain.model.ChallengeVisual
import com.personal.sleepalarm.domain.model.GeometrySegment
import com.personal.sleepalarm.domain.model.GraphSeries
import com.personal.sleepalarm.domain.model.MathAnswerSpec
import com.personal.sleepalarm.domain.model.MathBoundary
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathChallengeKind
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.domain.model.MathInterval
import com.personal.sleepalarm.domain.model.NumberLineInterval
import com.personal.sleepalarm.domain.model.NumberLinePoint
import com.personal.sleepalarm.domain.model.VisualPoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Stable identifiers for the advanced, parameterised wake-up templates.
 *
 * They deliberately live outside [MathChallenge]: an alarm still stores a compact challenge,
 * while tests can address every family directly instead of hoping that random sampling reaches it.
 */
internal enum class EquationTemplateId(val difficulty: MathDifficulty) {
    EXPERT_LINEAR_BRACKETS(MathDifficulty.EXPERT),
    EXPERT_FRACTION_SUM(MathDifficulty.EXPERT),
    EXPERT_SYSTEM_SUM(MathDifficulty.EXPERT),
    EXPERT_QUADRATIC_VIETA(MathDifficulty.EXPERT),
    EXPERT_BIQUADRATIC_FILTER(MathDifficulty.EXPERT),
    EXPERT_ABSOLUTE_TWO_BREAKS(MathDifficulty.EXPERT),
    EXPERT_RADICAL_SUM(MathDifficulty.EXPERT),
    EXPERT_EXPONENTIAL_SUM(MathDifficulty.EXPERT),
    EXPERT_LOG_PRODUCT(MathDifficulty.EXPERT),
    EXPERT_LOG_RATIO(MathDifficulty.EXPERT),
    EXPERT_ARITHMETIC_PROGRESSION(MathDifficulty.EXPERT),
    EXPERT_GEOMETRIC_PROGRESSION(MathDifficulty.EXPERT),
    EXPERT_DIGIT_SYSTEM(MathDifficulty.EXPERT),
    EXPERT_RATIONAL_PROPORTION(MathDifficulty.EXPERT),
    EXPERT_STRUCTURED_SYSTEM_PAIR(MathDifficulty.EXPERT),
    EXPERT_STRUCTURED_QUADRATIC_ROOT_SET(MathDifficulty.EXPERT),
    EXPERT_STRUCTURED_QUADRATIC_INEQUALITY(MathDifficulty.EXPERT),
    EXPERT_STRUCTURED_PARABOLA_VERTEX(MathDifficulty.EXPERT),

    EXTREME_RATIONAL_TWO_FRACTIONS(MathDifficulty.EXTREME),
    EXTREME_NONLINEAR_SYSTEM(MathDifficulty.EXTREME),
    EXTREME_CUBIC_FILTER(MathDifficulty.EXTREME),
    EXTREME_EXPONENTIAL_MIXED_SUM(MathDifficulty.EXTREME),
    EXTREME_EXPONENTIAL_COMMON_BASES(MathDifficulty.EXTREME),
    EXTREME_LOG_THREE_FACTORS(MathDifficulty.EXTREME),
    EXTREME_CRT_THREE(MathDifficulty.EXTREME),
    EXTREME_GCD_LCM_PAIR(MathDifficulty.EXTREME),
    EXTREME_DIVISOR_COUNT(MathDifficulty.EXTREME),
    EXTREME_LAST_TWO_DIGITS(MathDifficulty.EXTREME),
    EXTREME_COMBINATION_SUM(MathDifficulty.EXTREME),
    EXTREME_COMBINATION_PRODUCT(MathDifficulty.EXTREME),
    EXTREME_AFFINE_RECURRENCE(MathDifficulty.EXTREME),
    EXTREME_PARABOLA_MINIMUM(MathDifficulty.EXTREME),
    EXTREME_POLYNOMIAL_DERIVATIVE(MathDifficulty.EXTREME),
    EXTREME_RATIONAL_INEQUALITY_COUNT(MathDifficulty.EXTREME),
    EXTREME_TRIG_INTEGER_ROOT_COUNT(MathDifficulty.EXTREME),
    EXTREME_LINEAR_CONGRUENCE(MathDifficulty.EXTREME),
    EXTREME_ABSOLUTE_INTEGER_MINIMUM(MathDifficulty.EXTREME),
    EXTREME_GEOMETRIC_SERIES_INVERSE(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_RATIONAL_INEQUALITY(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_BIQUADRATIC_INEQUALITY(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_NESTED_ABSOLUTE(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_PARAMETER_RANGE(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_TRIG_ROOT_SET(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_MODULAR_ROOT_SET(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_POLYNOMIAL_ROOT_SET(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_LINE_INTERSECTION(MathDifficulty.EXTREME),
    EXTREME_STRUCTURED_MIDPOINT(MathDifficulty.EXTREME)
}

/**
 * Advanced equation and short-answer generator used by EXPERT and EXTREME.
 *
 * Every template is original and generated from its answer. It has one requested positive integer
 * answer in 1..9999. Domain restrictions are part of the displayed problem whenever the underlying
 * equation has other real roots. No differential equations or integral tasks are generated.
 */
internal object EquationChallengeGenerator {

    internal val expertTemplates: List<EquationTemplateId> =
        EquationTemplateId.entries.filter { it.difficulty == MathDifficulty.EXPERT }

    internal val extremeTemplates: List<EquationTemplateId> =
        EquationTemplateId.entries.filter { it.difficulty == MathDifficulty.EXTREME }

    fun generate(
        difficulty: MathDifficulty,
        random: Random = Random.Default
    ): MathChallenge {
        val templates = when (difficulty) {
            MathDifficulty.EXPERT -> expertTemplates
            MathDifficulty.EXTREME -> extremeTemplates
            else -> error("EquationChallengeGenerator supports only EXPERT and EXTREME")
        }
        return generateTemplate(templates[random.nextInt(templates.size)], random)
    }

    internal fun generateTemplate(
        template: EquationTemplateId,
        random: Random
    ): MathChallenge {
        val generated = when (template) {
            EquationTemplateId.EXPERT_LINEAR_BRACKETS -> expertLinearBrackets(random)
            EquationTemplateId.EXPERT_FRACTION_SUM -> expertFractionSum(random)
            EquationTemplateId.EXPERT_SYSTEM_SUM -> expertSystemSum(random)
            EquationTemplateId.EXPERT_QUADRATIC_VIETA -> expertQuadraticVieta(random)
            EquationTemplateId.EXPERT_BIQUADRATIC_FILTER -> expertBiquadraticFilter(random)
            EquationTemplateId.EXPERT_ABSOLUTE_TWO_BREAKS -> expertAbsoluteTwoBreaks(random)
            EquationTemplateId.EXPERT_RADICAL_SUM -> expertRadicalSum(random)
            EquationTemplateId.EXPERT_EXPONENTIAL_SUM -> expertExponentialSum(random)
            EquationTemplateId.EXPERT_LOG_PRODUCT -> expertLogProduct(random)
            EquationTemplateId.EXPERT_LOG_RATIO -> expertLogRatio(random)
            EquationTemplateId.EXPERT_ARITHMETIC_PROGRESSION -> expertArithmeticProgression(random)
            EquationTemplateId.EXPERT_GEOMETRIC_PROGRESSION -> expertGeometricProgression(random)
            EquationTemplateId.EXPERT_DIGIT_SYSTEM -> expertDigitSystem(random)
            EquationTemplateId.EXPERT_RATIONAL_PROPORTION -> expertRationalProportion(random)
            EquationTemplateId.EXPERT_STRUCTURED_SYSTEM_PAIR -> expertStructuredSystemPair(random)
            EquationTemplateId.EXPERT_STRUCTURED_QUADRATIC_ROOT_SET -> expertStructuredQuadraticRootSet(random)
            EquationTemplateId.EXPERT_STRUCTURED_QUADRATIC_INEQUALITY -> expertStructuredQuadraticInequality(random)
            EquationTemplateId.EXPERT_STRUCTURED_PARABOLA_VERTEX -> expertStructuredParabolaVertex(random)
            EquationTemplateId.EXTREME_RATIONAL_TWO_FRACTIONS -> extremeRationalTwoFractions(random)
            EquationTemplateId.EXTREME_NONLINEAR_SYSTEM -> extremeNonlinearSystem(random)
            EquationTemplateId.EXTREME_CUBIC_FILTER -> extremeCubicFilter(random)
            EquationTemplateId.EXTREME_EXPONENTIAL_MIXED_SUM -> extremeExponentialMixedSum(random)
            EquationTemplateId.EXTREME_EXPONENTIAL_COMMON_BASES -> extremeExponentialCommonBases(random)
            EquationTemplateId.EXTREME_LOG_THREE_FACTORS -> extremeLogThreeFactors(random)
            EquationTemplateId.EXTREME_CRT_THREE -> extremeCrtThree(random)
            EquationTemplateId.EXTREME_GCD_LCM_PAIR -> extremeGcdLcmPair(random)
            EquationTemplateId.EXTREME_DIVISOR_COUNT -> extremeDivisorCount(random)
            EquationTemplateId.EXTREME_LAST_TWO_DIGITS -> extremeLastTwoDigits(random)
            EquationTemplateId.EXTREME_COMBINATION_SUM -> extremeCombinationSum(random)
            EquationTemplateId.EXTREME_COMBINATION_PRODUCT -> extremeCombinationProduct(random)
            EquationTemplateId.EXTREME_AFFINE_RECURRENCE -> extremeAffineRecurrence(random)
            EquationTemplateId.EXTREME_PARABOLA_MINIMUM -> extremeParabolaMinimum(random)
            EquationTemplateId.EXTREME_POLYNOMIAL_DERIVATIVE -> extremePolynomialDerivative(random)
            EquationTemplateId.EXTREME_RATIONAL_INEQUALITY_COUNT -> extremeRationalInequalityCount(random)
            EquationTemplateId.EXTREME_TRIG_INTEGER_ROOT_COUNT -> extremeTrigIntegerRootCount(random)
            EquationTemplateId.EXTREME_LINEAR_CONGRUENCE -> extremeLinearCongruence(random)
            EquationTemplateId.EXTREME_ABSOLUTE_INTEGER_MINIMUM -> extremeAbsoluteIntegerMinimum(random)
            EquationTemplateId.EXTREME_GEOMETRIC_SERIES_INVERSE -> extremeGeometricSeriesInverse(random)
            EquationTemplateId.EXTREME_STRUCTURED_RATIONAL_INEQUALITY -> extremeStructuredRationalInequality(random)
            EquationTemplateId.EXTREME_STRUCTURED_BIQUADRATIC_INEQUALITY -> extremeStructuredBiquadraticInequality(random)
            EquationTemplateId.EXTREME_STRUCTURED_NESTED_ABSOLUTE -> extremeStructuredNestedAbsolute(random)
            EquationTemplateId.EXTREME_STRUCTURED_PARAMETER_RANGE -> extremeStructuredParameterRange(random)
            EquationTemplateId.EXTREME_STRUCTURED_TRIG_ROOT_SET -> extremeStructuredTrigRootSet(random)
            EquationTemplateId.EXTREME_STRUCTURED_MODULAR_ROOT_SET -> extremeStructuredModularRootSet(random)
            EquationTemplateId.EXTREME_STRUCTURED_POLYNOMIAL_ROOT_SET -> extremeStructuredPolynomialRootSet(random)
            EquationTemplateId.EXTREME_STRUCTURED_LINE_INTERSECTION -> extremeStructuredLineIntersection(random)
            EquationTemplateId.EXTREME_STRUCTURED_MIDPOINT -> extremeStructuredMidpoint(random)
        }

        check(generated.difficulty == template.difficulty)
        when (val spec = generated.answerSpec) {
            is MathAnswerSpec.Integer -> {
                check(spec.expected in MIN_ANSWER.toLong()..MAX_ANSWER.toLong())
                check(generated.answer.toLong() == spec.expected)
            }
            is MathAnswerSpec.IntegerSet -> check(spec.expected.all { it in -MAX_STRUCTURED_VALUE..MAX_STRUCTURED_VALUE })
            is MathAnswerSpec.IntervalSet -> check(spec.expected.isNotEmpty())
            is MathAnswerSpec.OrderedPair -> Unit
        }
        check(generated.question.length <= MAX_QUESTION_LENGTH)
        return generated
    }

    // EXPERT -----------------------------------------------------------------

    private fun expertLinearBrackets(random: Random): MathChallenge {
        val answer = random.nextInt(5, 51)
        val a = random.nextInt(5, 10)
        val b = random.nextInt(2, 5)
        val c = random.nextInt(1, a + b)
        val p = random.nextInt(6, 13)
        val q = random.nextInt(1, 5)
        val rightConstant = (a + b - c) * answer + a * p - b * q
        return challenge(
            "$a(x + $p) + $b(x - $q) = $c·x + $rightConstant",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.LINEAR
        )
    }

    private fun expertFractionSum(random: Random): MathChallenge {
        val answer = random.nextInt(3, 25)
        val p = random.nextInt(2, 7)
        var q = random.nextInt(3, 9)
        if (q == p) q += 1
        val leftValue = random.nextInt(answer + 2, answer + 8)
        val rightValue = random.nextInt(answer + 2, answer + 8)
        val leftOffset = p * leftValue - answer
        val rightOffset = q * rightValue - answer
        return challenge(
            "(x + $leftOffset) / $p + (x + $rightOffset) / $q = ${leftValue + rightValue}",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.FRACTION
        )
    }

    private fun expertSystemSum(random: Random): MathChallenge {
        val x = random.nextInt(3, 31)
        val y = random.nextInt(2, 25)
        val a = random.nextInt(2, 7)
        val b = random.nextInt(1, 6)
        val c = random.nextInt(1, 6)
        var d = random.nextInt(2, 8)
        if (a * d == b * c) d += 1
        return challenge(
            "$a·x + $b·y = ${a * x + b * y}\n" +
                "$c·x + $d·y = ${c * x + d * y}\n" +
                "x + y = ?",
            x + y,
            MathDifficulty.EXPERT,
            MathChallengeKind.SYSTEM
        )
    }

    private fun expertQuadraticVieta(random: Random): MathChallenge {
        val smallerRoot = random.nextInt(2, 18)
        val answer = random.nextInt(smallerRoot + 3, smallerRoot + 18)
        return challenge(
            "x² - ${smallerRoot + answer}·x + ${smallerRoot * answer} = 0\n" +
                "x > $smallerRoot",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.BIQUADRATIC
        )
    }

    private fun expertBiquadraticFilter(random: Random): MathChallenge {
        val answer = random.nextInt(2, 8)
        val largerRoot = random.nextInt(answer + 2, answer + 8)
        val answerSquared = answer * answer
        val largerSquared = largerRoot * largerRoot
        return challenge(
            "x⁴ - ${answerSquared + largerSquared}·x² + ${answerSquared * largerSquared} = 0\n" +
                "0 < x < $largerRoot",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.QUADRATIC
        )
    }

    private fun expertAbsoluteTwoBreaks(random: Random): MathChallenge {
        val firstBreak = random.nextInt(2, 15)
        val secondBreak = random.nextInt(firstBreak + 2, firstBreak + 15)
        val answer = random.nextInt(secondBreak + 2, secondBreak + 22)
        val rightSide = 2 * answer - firstBreak - secondBreak
        return challenge(
            "|x - $firstBreak| + |x - $secondBreak| = $rightSide\n" +
                "x > $secondBreak",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.ABSOLUTE
        )
    }

    private fun expertRadicalSum(random: Random): MathChallenge {
        val smallRoot = random.nextInt(2, 5)
        val largeRoot = random.nextInt(smallRoot + 2, smallRoot + 6)
        val answer = random.nextInt(smallRoot * smallRoot + 1, largeRoot * largeRoot)
        val plusOffset = largeRoot * largeRoot - answer
        val minusOffset = answer - smallRoot * smallRoot
        return challenge(
            "√(x + $plusOffset) + √(x - $minusOffset) = ${largeRoot + smallRoot}",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.RADICAL
        )
    }

    private fun expertExponentialSum(random: Random): MathChallenge {
        val answer = random.nextInt(6, 11)
        val firstShift = random.nextInt(1, 4)
        var secondShift = random.nextInt(2, 6)
        if (secondShift == firstShift) secondShift += 1
        val rightSide = powInt(2, answer - firstShift) + powInt(2, answer - secondShift)
        return challenge(
            "2^(x - $firstShift) + 2^(x - $secondShift) = $rightSide",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.EXPONENTIAL
        )
    }

    private fun expertLogProduct(random: Random): MathChallenge {
        val firstPower = random.nextInt(2, 5)
        val secondPower = random.nextInt(1, 4)
        val largestPowerValue = maxOf(powInt(2, firstPower), powInt(2, secondPower))
        val answer = random.nextInt(largestPowerValue + 5, largestPowerValue + 46)
        val firstOffset = answer - powInt(2, firstPower)
        val secondOffset = answer - powInt(2, secondPower)
        return challenge(
            "log₂(x - $firstOffset) + log₂(x - $secondOffset) = ${firstPower + secondPower}",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.LOGARITHM
        )
    }

    private fun expertLogRatio(random: Random): MathChallenge {
        val answer = random.nextInt(12, 61)
        val exponent = random.nextInt(1, 4)
        val ratio = powInt(2, exponent)
        val denominatorAtAnswer = random.nextInt(answer / ratio + 1, answer)
        val plusOffset = ratio * denominatorAtAnswer - answer
        val minusOffset = answer - denominatorAtAnswer
        return challenge(
            "log₂(x + $plusOffset) - log₂(x - $minusOffset) = $exponent",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.LOGARITHM
        )
    }

    private fun expertArithmeticProgression(random: Random): MathChallenge {
        val first = random.nextInt(2, 31)
        val difference = random.nextInt(3, 13)
        val third = first + 2 * difference
        val ninth = first + 8 * difference
        val answer = first + 14 * difference
        return challenge(
            "a₃ = $third,  a₉ = $ninth\n" +
                "aₙ₊₁ - aₙ = const\n" +
                "a₁₅ = ?",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.SEQUENCE
        )
    }

    private fun expertGeometricProgression(random: Random): MathChallenge {
        val first = random.nextInt(1, 5)
        val ratio = random.nextInt(2, 4)
        val second = first * ratio
        val fifth = first * powInt(ratio, 4)
        val answer = first * powInt(ratio, 6)
        return challenge(
            "b₂ = $second,  b₅ = $fifth,  q > 0\n" +
                "bₙ₊₁ / bₙ = q\n" +
                "b₇ = ?",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.SEQUENCE
        )
    }

    private fun expertDigitSystem(random: Random): MathChallenge {
        val tens = random.nextInt(1, 10)
        val units = random.nextInt(0, 10)
        return challenge(
            "a + b = ${tens + units}\n" +
                "a - b = ${tens - units}\n" +
                "a ∈ {1…9},  b ∈ {0…9}\n" +
                "10a + b = ?",
            10 * tens + units,
            MathDifficulty.EXPERT,
            MathChallengeKind.DIGIT
        )
    }

    private fun expertRationalProportion(random: Random): MathChallenge {
        val denominator = random.nextInt(4, 9)
        val numerator = random.nextInt(1, denominator)
        val scale = random.nextInt(4, 12)
        val added = random.nextInt(1, (denominator - numerator) * scale)
        val answer = denominator * scale - added
        val subtracted = answer - numerator * scale
        return challenge(
            "(x - $subtracted) / (x + $added) = $numerator / $denominator\n" +
                "x > 0",
            answer,
            MathDifficulty.EXPERT,
            MathChallengeKind.RATIONAL
        )
    }

    private fun expertStructuredSystemPair(random: Random): MathChallenge {
        val x = random.nextInt(-7, 8)
        val y = random.nextInt(-7, 8)
        val a = random.nextInt(2, 7)
        val b = random.nextInt(1, 6)
        val c = random.nextInt(1, 6)
        var d = random.nextInt(2, 8)
        if (a * d == b * c) d += 1
        val firstResult = a * x + b * y
        val secondResult = c * x + d * y
        return MathChallenge(
            question = "$a·x + $b·y = $firstResult\n" +
                "$c·x + $d·y = $secondResult\n" +
                "(x; y) = ?",
            answerSpec = MathAnswerSpec.OrderedPair(x, y),
            difficulty = MathDifficulty.EXPERT,
            kind = MathChallengeKind.COORDINATE,
            visual = systemGraphVisual(a, b, firstResult, c, d, secondResult, x, y)
        )
    }

    private fun expertStructuredQuadraticRootSet(random: Random): MathChallenge {
        val firstRoot = random.nextInt(2, 13)
        val secondRoot = random.nextInt(firstRoot + 3, firstRoot + 16)
        return MathChallenge(
            question = "x² - ${firstRoot + secondRoot}·x + ${firstRoot * secondRoot} = 0\n" +
                "{x ∈ ℝ} = ?",
            answerSpec = MathAnswerSpec.IntegerSet(setOf(firstRoot.toLong(), secondRoot.toLong())),
            difficulty = MathDifficulty.EXPERT,
            kind = MathChallengeKind.NUMBER_SET
        )
    }

    private fun expertStructuredQuadraticInequality(random: Random): MathChallenge {
        val lower = random.nextInt(-10, 3)
        var upper = random.nextInt(maxOf(lower + 3, 3), 15)
        if (lower + upper == 0) upper += 1
        val interval = MathInterval.closed(lower, upper)
        return MathChallenge(
            question = "x² ${signedCoefficient(-(lower + upper), "x")} " +
                "${signedConstant(lower * upper)} ≤ 0\n" +
                "x ∈ ?",
            answerSpec = MathAnswerSpec.IntervalSet(listOf(interval)),
            difficulty = MathDifficulty.EXPERT,
            kind = MathChallengeKind.INEQUALITY,
            visual = numberLineVisual(
                intervals = listOf(interval),
                min = lower - 5.0,
                max = upper + 5.0,
                description = "Closed solution interval from $lower to $upper"
            )
        )
    }

    private fun expertStructuredParabolaVertex(random: Random): MathChallenge {
        val coefficient = random.nextInt(2, 7)
        val vertexX = random.nextInt(2, 9)
        val vertexY = random.nextInt(1, 21)
        val linear = 2 * coefficient * vertexX
        val constant = coefficient * vertexX * vertexX + vertexY
        return MathChallenge(
            question = "y = $coefficient·x² - $linear·x + $constant\n" +
                "vertex (x; y) = ?",
            answerSpec = MathAnswerSpec.OrderedPair(vertexX, vertexY),
            difficulty = MathDifficulty.EXPERT,
            kind = MathChallengeKind.FUNCTION,
            visual = parabolaVisual(coefficient, vertexX, vertexY)
        )
    }

    // EXTREME ----------------------------------------------------------------

    private fun extremeRationalTwoFractions(random: Random): MathChallenge {
        val answer = random.nextInt(4, 41)
        val firstOffset = random.nextInt(2, 11)
        var secondOffset = random.nextInt(3, 13)
        if (secondOffset == firstOffset) secondOffset += 1
        val firstWeight = random.nextInt(2, 7)
        val secondWeight = random.nextInt(2, 7)
        val firstNumerator = firstWeight * (answer + firstOffset)
        val secondNumerator = secondWeight * (answer + secondOffset)
        return challenge(
            "$firstNumerator / (x + $firstOffset) + " +
                "$secondNumerator / (x + $secondOffset) = ${firstWeight + secondWeight}\n" +
                "x > 0",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.RATIONAL
        )
    }

    private fun extremeNonlinearSystem(random: Random): MathChallenge {
        val smaller = random.nextInt(2, 18)
        val answer = random.nextInt(smaller + 3, smaller + 22)
        return challenge(
            "x + y = ${answer + smaller}\n" +
                "x·y = ${answer * smaller}\n" +
                "x > y\n" +
                "x = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.SYSTEM
        )
    }

    private fun extremeCubicFilter(random: Random): MathChallenge {
        val firstRoot = random.nextInt(1, 8)
        val secondRoot = random.nextInt(firstRoot + 2, firstRoot + 9)
        val answer = random.nextInt(secondRoot + 2, secondRoot + 10)
        val pairSum = firstRoot * secondRoot + firstRoot * answer + secondRoot * answer
        val product = firstRoot * secondRoot * answer
        return challenge(
            "x³ - ${firstRoot + secondRoot + answer}·x² + $pairSum·x - $product = 0\n" +
                "x > $secondRoot",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.POLYNOMIAL
        )
    }

    private fun extremeExponentialMixedSum(random: Random): MathChallenge {
        val answer = random.nextInt(5, 9)
        val firstShift = random.nextInt(1, 4)
        val secondShift = random.nextInt(1, 4)
        val rightSide = powInt(2, answer - firstShift) + powInt(4, answer - secondShift)
        return challenge(
            "2^(x - $firstShift) + 4^(x - $secondShift) = $rightSide",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.EXPONENTIAL
        )
    }

    private fun extremeExponentialCommonBases(random: Random): MathChallenge {
        val answer = random.nextInt(8, 14)
        val firstShift = random.nextInt(1, 4)
        val secondShift = random.nextInt(3, 7)
        val extraPower = answer - 3 * firstShift + 2 * secondShift
        return challenge(
            "8^(x - $firstShift) = 4^(x - $secondShift) · 2^$extraPower",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.EXPONENTIAL
        )
    }

    private fun extremeLogThreeFactors(random: Random): MathChallenge {
        val p = random.nextInt(1, 4)
        val q = random.nextInt(2, 5)
        val r = random.nextInt(1, 5)
        val largestPower = maxOf(powInt(2, p), powInt(2, q), powInt(2, r))
        val answer = random.nextInt(largestPower + 7, largestPower + 60)
        val firstOffset = answer - powInt(2, p)
        val secondOffset = answer - powInt(2, q)
        val thirdOffset = answer - powInt(2, r)
        return challenge(
            "log₂(x - $firstOffset) + log₂(x - $secondOffset) +\n" +
                "log₂(x - $thirdOffset) = ${p + q + r}",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.LOGARITHM
        )
    }

    private fun extremeCrtThree(random: Random): MathChallenge {
        val modulusSets = listOf(
            intArrayOf(3, 5, 7),
            intArrayOf(4, 5, 9),
            intArrayOf(5, 7, 8),
            intArrayOf(7, 8, 9)
        )
        val moduli = modulusSets[random.nextInt(modulusSets.size)]
        val product = moduli.fold(1) { acc, value -> acc * value }
        val answer = random.nextInt(1, product + 1)
        return challenge(
            "n ≡ ${answer % moduli[0]} (mod ${moduli[0]})\n" +
                "n ≡ ${answer % moduli[1]} (mod ${moduli[1]})\n" +
                "n ≡ ${answer % moduli[2]} (mod ${moduli[2]})\n" +
                "min(n > 0) = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.NUMBER_THEORY
        )
    }

    private fun extremeGcdLcmPair(random: Random): MathChallenge {
        val coprimePairs = listOf(
            4 to 9,
            5 to 12,
            7 to 10,
            8 to 15,
            9 to 14,
            11 to 18,
            13 to 20
        )
        val (smallFactor, largeFactor) = coprimePairs[random.nextInt(coprimePairs.size)]
        val commonDivisor = random.nextInt(2, 13)
        val smaller = commonDivisor * smallFactor
        val answer = commonDivisor * largeFactor
        val leastCommonMultiple = commonDivisor * smallFactor * largeFactor
        return challenge(
            "gcd(a, b) = $commonDivisor\n" +
                "lcm(a, b) = $leastCommonMultiple\n" +
                "a + b = ${smaller + answer},  a < b\n" +
                "b = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.NUMBER_THEORY
        )
    }

    private fun extremeDivisorCount(random: Random): MathChallenge {
        val primeTriples = listOf(
            intArrayOf(2, 3, 5),
            intArrayOf(2, 3, 7),
            intArrayOf(2, 5, 7),
            intArrayOf(3, 5, 7)
        )
        val primes = primeTriples[random.nextInt(primeTriples.size)]
        val exponents = intArrayOf(
            random.nextInt(2, 6),
            random.nextInt(1, 4),
            random.nextInt(1, 3)
        )
        var number = 1L
        for (index in primes.indices) {
            number *= powLong(primes[index].toLong(), exponents[index])
        }
        val answer = exponents.fold(1) { acc, exponent -> acc * (exponent + 1) }
        return challenge(
            "|{d ∈ ℕ : d | $number}| = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.DIVISORS
        )
    }

    private fun extremeLastTwoDigits(random: Random): MathChallenge {
        val bases = intArrayOf(3, 7, 9, 11, 13, 17, 19, 21, 23, 27)
        val base = bases[random.nextInt(bases.size)]
        val exponent = random.nextInt(17, 81)
        val answer = modularPower(base, exponent, 100)
        return challenge(
            "$base^$exponent mod 100 = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.NUMBER_THEORY
        )
    }

    private fun extremeCombinationSum(random: Random): MathChallenge {
        val answer = random.nextInt(7, 18)
        val rightSide = combination(answer, 2) + combination(answer, 3)
        return challenge(
            "C(x, 2) + C(x, 3) = $rightSide\n" +
                "x ∈ ℕ,  x ≥ 3",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.COMBINATORICS
        )
    }

    private fun extremeCombinationProduct(random: Random): MathChallenge {
        val answer = random.nextInt(6, 17)
        val rightSide = combination(answer, 2) * (answer - 2)
        return challenge(
            "C(x, 2) · (x - 2) = $rightSide\n" +
                "x ∈ ℕ,  x ≥ 3",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.COMBINATORICS
        )
    }

    private fun extremeAffineRecurrence(random: Random): MathChallenge {
        val first = random.nextInt(2, 18)
        val multiplier = random.nextInt(2, 4)
        val increment = random.nextInt(1, 10)
        var current = first
        repeat(4) { current = multiplier * current + increment }
        return challenge(
            "a₁ = $first\n" +
                "aₙ₊₁ = $multiplier·aₙ + $increment\n" +
                "a₅ = ?",
            current,
            MathDifficulty.EXTREME,
            MathChallengeKind.SEQUENCE
        )
    }

    private fun extremeParabolaMinimum(random: Random): MathChallenge {
        val coefficient = random.nextInt(2, 9)
        val vertex = random.nextInt(2, 13)
        val answer = random.nextInt(1, 61)
        val linear = 2 * coefficient * vertex
        val constant = coefficient * vertex * vertex + answer
        return challenge(
            "f(x) = $coefficient·x² - $linear·x + $constant\n" +
                "min(x ∈ ℝ) f(x) = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.FUNCTION
        )
    }

    private fun extremePolynomialDerivative(random: Random): MathChallenge {
        val cubic = random.nextInt(1, 6)
        val quadratic = random.nextInt(2, 9)
        val linear = random.nextInt(1, 15)
        val constant = random.nextInt(1, 31)
        val point = random.nextInt(2, 8)
        val answer = 3 * cubic * point * point + 2 * quadratic * point + linear
        return challenge(
            "f(x) = $cubic·x³ + $quadratic·x² + $linear·x + $constant\n" +
                "f′($point) = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.ANALYSIS
        )
    }

    private fun extremeRationalInequalityCount(random: Random): MathChallenge {
        val firstRoot = random.nextInt(-8, 3)
        val secondRoot = random.nextInt(firstRoot + 2, 10)
        var pole = random.nextInt(firstRoot + 1, secondRoot)
        if (pole == 0 && secondRoot - firstRoot > 2) pole += 1
        val lower = firstRoot - random.nextInt(4, 10)
        val upper = secondRoot + random.nextInt(5, 13)
        val answer = (lower..upper).count { x ->
            x != pole && signOfProduct(x - firstRoot, x - secondRoot, x - pole) > 0
        }
        return challenge(
            "((x - ${formatSignedConstant(firstRoot)})" +
                "(x - ${formatSignedConstant(secondRoot)})) / " +
                "(x - ${formatSignedConstant(pole)}) > 0\n" +
                "x ∈ ℤ ∩ [$lower; $upper]\n" +
                "number of x = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.INEQUALITY
        )
    }

    private fun extremeTrigIntegerRootCount(random: Random): MathChallenge {
        val upper = random.nextInt(24, 73)
        val answer = (0..upper).count { value -> value % 12 == 1 || value % 12 == 5 }
        return challenge(
            "sin(π·x / 6) = 1 / 2\n" +
                "x ∈ ℤ ∩ [0; $upper]\n" +
                "number of x = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.TRIGONOMETRY
        )
    }

    private fun extremeLinearCongruence(random: Random): MathChallenge {
        val primeModuli = intArrayOf(17, 19, 23, 29, 31, 37, 41, 43)
        val modulus = primeModuli[random.nextInt(primeModuli.size)]
        val coefficient = random.nextInt(2, modulus)
        val answer = random.nextInt(1, modulus)
        val remainder = coefficient * answer % modulus
        return challenge(
            "$coefficient·x ≡ $remainder (mod $modulus)\n" +
                "1 ≤ x < $modulus",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.NUMBER_THEORY
        )
    }

    private fun extremeAbsoluteIntegerMinimum(random: Random): MathChallenge {
        val firstCoefficient = random.nextInt(2, 8)
        val secondCoefficient = random.nextInt(2, 8)
        val firstOffset = random.nextInt(7, 80)
        var secondOffset = random.nextInt(7, 80)
        if (firstOffset * secondCoefficient == secondOffset * firstCoefficient) {
            secondOffset += 1
        }
        val answer = (0..50).minOf { x ->
            abs(firstCoefficient * x - firstOffset) +
                abs(secondCoefficient * x - secondOffset)
        }
        return challenge(
            "min(x ∈ ℤ ∩ [0; 50])\n" +
                "|$firstCoefficient·x - $firstOffset| + " +
                "|$secondCoefficient·x - $secondOffset| = ?",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.FUNCTION
        )
    }

    private fun extremeGeometricSeriesInverse(random: Random): MathChallenge {
        val answer = random.nextInt(2, 7)
        val rightSide = (0..4).sumOf { exponent -> powInt(answer, exponent) }
        return challenge(
            "1 + x + x² + x³ + x⁴ = $rightSide\n" +
                "x ∈ ℕ,  x > 1",
            answer,
            MathDifficulty.EXTREME,
            MathChallengeKind.SEQUENCE
        )
    }

    private fun extremeStructuredRationalInequality(random: Random): MathChallenge {
        val zero = random.nextInt(-9, 3)
        val pole = random.nextInt(maxOf(zero + 3, 3), 16)
        val intervals = listOf(
            MathInterval.atMost(zero, inclusive = false),
            MathInterval.atLeast(pole, inclusive = false)
        )
        return MathChallenge(
            question = "(x ${signedConstant(-zero)}) / (x ${signedConstant(-pole)}) > 0\n" +
                "x ∈ ?",
            answerSpec = MathAnswerSpec.IntervalSet(intervals),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.INEQUALITY,
            visual = numberLineVisual(
                intervals = intervals,
                min = zero - 7.0,
                max = pole + 7.0,
                description = "Two open rays separated by $zero and $pole"
            )
        )
    }

    private fun extremeStructuredBiquadraticInequality(random: Random): MathChallenge {
        val inner = random.nextInt(2, 7)
        val outer = random.nextInt(inner + 2, inner + 8)
        val innerSquared = inner * inner
        val outerSquared = outer * outer
        val intervals = listOf(
            MathInterval.closed(-outer, -inner),
            MathInterval.closed(inner, outer)
        )
        return MathChallenge(
            question = "x⁴ - ${innerSquared + outerSquared}·x² + " +
                "${innerSquared * outerSquared} ≤ 0\n" +
                "x ∈ ?",
            answerSpec = MathAnswerSpec.IntervalSet(intervals),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.BIQUADRATIC,
            visual = numberLineVisual(
                intervals = intervals,
                min = -outer - 4.0,
                max = outer + 4.0,
                description = "Two closed symmetric solution intervals"
            )
        )
    }

    private fun extremeStructuredNestedAbsolute(random: Random): MathChallenge {
        val center = random.nextInt(-6, 7)
        val radius = random.nextInt(5, 12)
        val tolerance = random.nextInt(1, radius)
        val near = radius - tolerance
        val far = radius + tolerance
        val intervals = listOf(
            MathInterval.closed(center - far, center - near),
            MathInterval.closed(center + near, center + far)
        )
        return MathChallenge(
            question = "||x ${signedConstant(-center)}| - $radius| ≤ $tolerance\n" +
                "x ∈ ?",
            answerSpec = MathAnswerSpec.IntervalSet(intervals),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.INEQUALITY
        )
    }

    private fun extremeStructuredParameterRange(random: Random): MathChallenge {
        val lower = random.nextInt(-10, 1)
        var upper = random.nextInt(2, 13)
        if (lower + upper == 0) upper += 1
        val sum = lower + upper
        val product = lower * upper
        val intervals = listOf(
            MathInterval.atMost(lower, inclusive = true),
            MathInterval.atLeast(upper, inclusive = true)
        )
        return MathChallenge(
            question = "x² - 2m·x ${signedCoefficient(sum, "m")} " +
                "${signedConstant(-product)} = 0\n" +
                "∃x ∈ ℝ;  m ∈ ?",
            answerSpec = MathAnswerSpec.IntervalSet(intervals),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.PARAMETER,
            visual = numberLineVisual(
                intervals = intervals,
                min = lower - 6.0,
                max = upper + 6.0,
                description = "Parameter values outside the interval from $lower to $upper"
            )
        )
    }

    private fun extremeStructuredTrigRootSet(random: Random): MathChallenge {
        val upper = random.nextInt(24, 61)
        val roots = (0..upper)
            .filter { value -> value % 12 == 1 || value % 12 == 5 }
            .map(Int::toLong)
            .toSet()
        return MathChallenge(
            question = "sin(π·x / 6) = 1 / 2\n" +
                "x ∈ ℤ ∩ [0; $upper]\n" +
                "{x} = ?",
            answerSpec = MathAnswerSpec.IntegerSet(roots),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.NUMBER_SET
        )
    }

    private fun extremeStructuredModularRootSet(random: Random): MathChallenge {
        val primes = intArrayOf(11, 13, 17, 19, 23, 29, 31)
        val modulus = primes[random.nextInt(primes.size)]
        val seedRoot = random.nextInt(1, modulus)
        val remainder = seedRoot * seedRoot % modulus
        val roots = (0 until modulus)
            .filter { candidate -> candidate * candidate % modulus == remainder }
            .map(Int::toLong)
            .toSet()
        return MathChallenge(
            question = "x² ≡ $remainder (mod $modulus)\n" +
                "x ∈ {0…${modulus - 1}}\n" +
                "{x} = ?",
            answerSpec = MathAnswerSpec.IntegerSet(roots),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.NUMBER_THEORY
        )
    }

    private fun extremeStructuredPolynomialRootSet(random: Random): MathChallenge {
        val first = random.nextInt(1, 7)
        val second = random.nextInt(first + 2, first + 8)
        val third = random.nextInt(second + 2, second + 8)
        val sum = first + second + third
        val pairSum = first * second + first * third + second * third
        val product = first * second * third
        return MathChallenge(
            question = "x³ - $sum·x² + $pairSum·x - $product = 0\n" +
                "{x ∈ ℝ} = ?",
            answerSpec = MathAnswerSpec.IntegerSet(
                setOf(first.toLong(), second.toLong(), third.toLong())
            ),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.POLYNOMIAL
        )
    }

    private fun extremeStructuredLineIntersection(random: Random): MathChallenge {
        val x = random.nextInt(-7, 8)
        val y = random.nextInt(-7, 8)
        val firstSlope = random.nextInt(1, 5)
        val secondSlope = random.nextInt(-4, 0)
        val firstIntercept = y - firstSlope * x
        val secondIntercept = y - secondSlope * x
        return MathChallenge(
            question = "y = $firstSlope·x ${signedConstant(firstIntercept)}\n" +
                "y = $secondSlope·x ${signedConstant(secondIntercept)}\n" +
                "intersection (x; y) = ?",
            answerSpec = MathAnswerSpec.OrderedPair(x, y),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.COORDINATE,
            visual = lineIntersectionVisual(
                firstSlope,
                firstIntercept,
                secondSlope,
                secondIntercept,
                x,
                y
            )
        )
    }

    private fun extremeStructuredMidpoint(random: Random): MathChallenge {
        val midpointX = random.nextInt(-7, 8)
        val midpointY = random.nextInt(-7, 8)
        val deltaX = random.nextInt(2, 7)
        val deltaY = random.nextInt(1, 6)
        val firstX = midpointX - deltaX
        val firstY = midpointY - deltaY
        val secondX = midpointX + deltaX
        val secondY = midpointY + deltaY
        val points = listOf(
            VisualPoint(firstX.toDouble(), firstY.toDouble(), label = "A"),
            VisualPoint(secondX.toDouble(), secondY.toDouble(), label = "B"),
            VisualPoint(midpointX.toDouble(), midpointY.toDouble(), label = "M", emphasized = true)
        )
        return MathChallenge(
            question = "A($firstX; $firstY),  B($secondX; $secondY)\n" +
                "M = midpoint(A, B)\n" +
                "M(x; y) = ?",
            answerSpec = MathAnswerSpec.OrderedPair(midpointX, midpointY),
            difficulty = MathDifficulty.EXTREME,
            kind = MathChallengeKind.GEOMETRY,
            visual = ChallengeVisual.GeometryDiagram(
                points = points,
                segments = listOf(GeometrySegment(0, 1)),
                contentDescription = "Segment AB with its midpoint M"
            )
        )
    }

    private fun challenge(
        question: String,
        answer: Int,
        difficulty: MathDifficulty,
        kind: MathChallengeKind
    ) = MathChallenge(
        question = question,
        answer = answer,
        difficulty = difficulty,
        kind = kind
    )

    private fun signedCoefficient(coefficient: Int, variable: String): String = when {
        coefficient > 0 -> "+ $coefficient·$variable"
        coefficient < 0 -> "- ${-coefficient}·$variable"
        else -> ""
    }

    private fun signedConstant(value: Int): String =
        if (value >= 0) "+ $value" else "- ${-value}"

    private fun systemGraphVisual(
        a: Int,
        b: Int,
        firstResult: Int,
        c: Int,
        d: Int,
        secondResult: Int,
        solutionX: Int,
        solutionY: Int
    ): ChallengeVisual.FunctionGraph {
        val xMin = solutionX - 6.0
        val xMax = solutionX + 6.0
        fun y(firstCoefficient: Int, secondCoefficient: Int, result: Int, x: Double): Double =
            (result - firstCoefficient * x) / secondCoefficient
        val firstSeries = GraphSeries(
            listOf(
                VisualPoint(xMin, y(a, b, firstResult, xMin)),
                VisualPoint(xMax, y(a, b, firstResult, xMax))
            )
        )
        val secondSeries = GraphSeries(
            listOf(
                VisualPoint(xMin, y(c, d, secondResult, xMin)),
                VisualPoint(xMax, y(c, d, secondResult, xMax))
            ),
            dashed = true
        )
        val allY = (firstSeries.points + secondSeries.points).map(VisualPoint::y) + solutionY.toDouble()
        return ChallengeVisual.FunctionGraph(
            xMin = xMin,
            xMax = xMax,
            yMin = allY.min() - 2.0,
            yMax = allY.max() + 2.0,
            series = listOf(firstSeries, secondSeries),
            points = listOf(
                VisualPoint(solutionX.toDouble(), solutionY.toDouble(), label = "?", emphasized = true)
            ),
            contentDescription = "Graphs of two equations with one marked intersection"
        )
    }

    private fun parabolaVisual(
        coefficient: Int,
        vertexX: Int,
        vertexY: Int
    ): ChallengeVisual.FunctionGraph {
        val xMin = vertexX - 6.0
        val xMax = vertexX + 6.0
        val points = (-6..6).map { delta ->
            val x = vertexX + delta.toDouble()
            val y = coefficient * delta.toDouble() * delta + vertexY
            VisualPoint(x, y)
        }
        return ChallengeVisual.FunctionGraph(
            xMin = xMin,
            xMax = xMax,
            yMin = vertexY - 3.0,
            yMax = points.maxOf(VisualPoint::y) + 3.0,
            series = listOf(GraphSeries(points)),
            points = listOf(
                VisualPoint(vertexX.toDouble(), vertexY.toDouble(), label = "?", emphasized = true)
            ),
            contentDescription = "Parabola with its vertex marked"
        )
    }

    private fun lineIntersectionVisual(
        firstSlope: Int,
        firstIntercept: Int,
        secondSlope: Int,
        secondIntercept: Int,
        solutionX: Int,
        solutionY: Int
    ): ChallengeVisual.FunctionGraph {
        val xMin = solutionX - 6.0
        val xMax = solutionX + 6.0
        fun series(slope: Int, intercept: Int, dashed: Boolean) = GraphSeries(
            points = listOf(
                VisualPoint(xMin, slope * xMin + intercept),
                VisualPoint(xMax, slope * xMax + intercept)
            ),
            dashed = dashed
        )
        val firstSeries = series(firstSlope, firstIntercept, dashed = false)
        val secondSeries = series(secondSlope, secondIntercept, dashed = true)
        val allY = (firstSeries.points + secondSeries.points).map(VisualPoint::y) + solutionY.toDouble()
        return ChallengeVisual.FunctionGraph(
            xMin = xMin,
            xMax = xMax,
            yMin = allY.min() - 2.0,
            yMax = allY.max() + 2.0,
            series = listOf(firstSeries, secondSeries),
            points = listOf(
                VisualPoint(solutionX.toDouble(), solutionY.toDouble(), label = "?", emphasized = true)
            ),
            contentDescription = "Two straight lines and their marked intersection"
        )
    }

    private fun numberLineVisual(
        intervals: List<MathInterval>,
        min: Double,
        max: Double,
        description: String
    ): ChallengeVisual.NumberLine = ChallengeVisual.NumberLine(
        min = min,
        max = max,
        intervals = intervals.map { interval ->
            NumberLineInterval(
                start = (interval.lower as? MathBoundary.Finite)?.value?.toDouble(),
                end = (interval.upper as? MathBoundary.Finite)?.value?.toDouble(),
                startInclusive = interval.lowerInclusive,
                endInclusive = interval.upperInclusive
            )
        },
        points = intervals.flatMap { interval ->
            buildList {
                (interval.lower as? MathBoundary.Finite)?.let { boundary ->
                    add(
                        NumberLinePoint(
                            value = boundary.value.toDouble(),
                            filled = interval.lowerInclusive
                        )
                    )
                }
                (interval.upper as? MathBoundary.Finite)?.let { boundary ->
                    add(
                        NumberLinePoint(
                            value = boundary.value.toDouble(),
                            filled = interval.upperInclusive
                        )
                    )
                }
            }
        }.distinctBy { point -> point.value },
        contentDescription = description
    )

    private fun powInt(base: Int, exponent: Int): Int {
        require(exponent >= 0)
        var result = 1
        repeat(exponent) { result = Math.multiplyExact(result, base) }
        return result
    }

    private fun powLong(base: Long, exponent: Int): Long {
        require(exponent >= 0)
        var result = 1L
        repeat(exponent) { result = Math.multiplyExact(result, base) }
        return result
    }

    private fun modularPower(base: Int, exponent: Int, modulus: Int): Int {
        var result = 1L
        var factor = (base % modulus).toLong()
        var remaining = exponent
        while (remaining > 0) {
            if (remaining and 1 == 1) result = result * factor % modulus
            factor = factor * factor % modulus
            remaining = remaining ushr 1
        }
        return result.toInt()
    }

    private fun combination(n: Int, k: Int): Int {
        val adjustedK = minOf(k, n - k)
        var result = 1L
        for (index in 1..adjustedK) {
            result = result * (n - adjustedK + index) / index
        }
        return result.toInt()
    }

    private fun signOfProduct(first: Int, second: Int, denominator: Int): Int {
        val numeratorSign = first.compareTo(0) * second.compareTo(0)
        return numeratorSign * denominator.compareTo(0)
    }

    /** `x - -3` is hard to read; keep each factor unambiguous. */
    private fun formatSignedConstant(value: Int): String =
        if (value >= 0) value.toString() else "($value)"

    private const val MIN_ANSWER = 1
    private const val MAX_ANSWER = 9_999
    private const val MAX_STRUCTURED_VALUE = 100_000L
    private const val MAX_QUESTION_LENGTH = 220
}
