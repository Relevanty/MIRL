package com.personal.sleepalarm.util

import com.personal.sleepalarm.domain.model.ChallengeVisual
import com.personal.sleepalarm.domain.model.MathAnswerParser
import com.personal.sleepalarm.domain.model.MathAnswerSpec
import com.personal.sleepalarm.domain.model.MathAnswerValidation
import com.personal.sleepalarm.domain.model.MathBoundary
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathChallengeKind
import com.personal.sleepalarm.domain.model.MathDifficulty
import com.personal.sleepalarm.domain.model.MathInterval
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationChallengeGeneratorTest {

    @Test
    fun catalogContainsEnoughDistinctAdvancedTemplates() {
        assertEquals(18, EquationChallengeGenerator.expertTemplates.distinct().size)
        assertEquals(29, EquationChallengeGenerator.extremeTemplates.distinct().size)
        assertTrue(EquationTemplateId.entries.size >= 46)
    }

    @Test
    fun everyTemplateKeepsItsContractAcrossThousandsOfSeeds() {
        EquationTemplateId.entries.forEachIndexed { index, template ->
            val random = Random(91_003 + index * 7_919)
            repeat(SAMPLES_PER_TEMPLATE) {
                val challenge = EquationChallengeGenerator.generateTemplate(template, random)
                assertEquals(template.difficulty, challenge.difficulty)
                assertValidAnswerShape(challenge)
                val canonical = MathAnswerParser.canonical(challenge.answerSpec)
                assertTrue(
                    "Canonical answer must validate: $canonical",
                    MathAnswerParser.validate(challenge.answerSpec, canonical) is MathAnswerValidation.Correct
                )
                assertTrue("Question is too long: ${challenge.question}", challenge.question.length <= 220)
                assertNoForbiddenCalculus(challenge.question)
                assertTrue(
                    "${template.name} is invalid/ambiguous:\n${challenge.question}\nanswer=${challenge.answer}",
                    independentlyValidates(template, challenge)
                )
            }
        }
    }

    @Test
    fun dispatcherReturnsBroadValidAdvancedCatalog() {
        val random = Random(2_026_0829)
        val seenKinds = linkedSetOf<MathChallengeKind>()
        repeat(8_000) { index ->
            val difficulty = if (index and 1 == 0) MathDifficulty.EXPERT else MathDifficulty.EXTREME
            val challenge = MathChallengeGenerator.generate(difficulty, random)
            assertEquals(difficulty, challenge.difficulty)
            assertValidAnswerShape(challenge)
            assertTrue(challenge.question.length <= 220)
            assertNoForbiddenCalculus(challenge.question)
            seenKinds += challenge.kind
        }
        assertTrue("Advanced generator exposes too few families: $seenKinds", seenKinds.size >= 17)
    }

    @Test
    fun structuredTemplatesAreLiveInRandomPoolAndCoverEveryAnswerAndVisualShape() {
        val random = Random(44_771)
        val answerShapes = linkedSetOf<Class<out MathAnswerSpec>>()
        val visualShapes = linkedSetOf<Class<out ChallengeVisual>>()
        repeat(20_000) { index ->
            val difficulty = if (index and 1 == 0) MathDifficulty.EXPERT else MathDifficulty.EXTREME
            val challenge = EquationChallengeGenerator.generate(difficulty, random)
            answerShapes += challenge.answerSpec.javaClass
            challenge.visual?.let { visualShapes += it.javaClass }
        }
        assertTrue(MathAnswerSpec.Integer::class.java in answerShapes)
        assertTrue(MathAnswerSpec.IntegerSet::class.java in answerShapes)
        assertTrue(MathAnswerSpec.IntervalSet::class.java in answerShapes)
        assertTrue(MathAnswerSpec.OrderedPair::class.java in answerShapes)
        assertTrue(ChallengeVisual.FunctionGraph::class.java in visualShapes)
        assertTrue(ChallengeVisual.NumberLine::class.java in visualShapes)
        assertTrue(ChallengeVisual.GeometryDiagram::class.java in visualShapes)

        val structured = EquationTemplateId.entries.filter { template ->
            EquationChallengeGenerator.generateTemplate(template, Random(template.ordinal)).answerSpec !is MathAnswerSpec.Integer
        }
        assertTrue("Structured templates must be part of the real catalog", structured.size >= 12)
        val visualCount = EquationTemplateId.entries.count { template ->
            EquationChallengeGenerator.generateTemplate(template, Random(template.ordinal + 1)).visual != null
        }
        assertTrue("At least four templates need code-native visuals", visualCount >= 4)
    }

    private fun independentlyValidates(
        template: EquationTemplateId,
        challenge: MathChallenge
    ): Boolean = when (template) {
        EquationTemplateId.EXPERT_LINEAR_BRACKETS -> validateExpertLinear(challenge)
        EquationTemplateId.EXPERT_FRACTION_SUM -> validateExpertFraction(challenge)
        EquationTemplateId.EXPERT_SYSTEM_SUM -> validateExpertSystem(challenge)
        EquationTemplateId.EXPERT_QUADRATIC_VIETA -> validateExpertQuadratic(challenge)
        EquationTemplateId.EXPERT_BIQUADRATIC_FILTER -> validateExpertBiquadratic(challenge)
        EquationTemplateId.EXPERT_ABSOLUTE_TWO_BREAKS -> validateExpertAbsolute(challenge)
        EquationTemplateId.EXPERT_RADICAL_SUM -> validateExpertRadical(challenge)
        EquationTemplateId.EXPERT_EXPONENTIAL_SUM -> validateExpertExponential(challenge)
        EquationTemplateId.EXPERT_LOG_PRODUCT -> validateExpertLogProduct(challenge)
        EquationTemplateId.EXPERT_LOG_RATIO -> validateExpertLogRatio(challenge)
        EquationTemplateId.EXPERT_ARITHMETIC_PROGRESSION -> validateArithmeticProgression(challenge)
        EquationTemplateId.EXPERT_GEOMETRIC_PROGRESSION -> validateGeometricProgression(challenge)
        EquationTemplateId.EXPERT_DIGIT_SYSTEM -> validateDigitSystem(challenge)
        EquationTemplateId.EXPERT_RATIONAL_PROPORTION -> validateRationalProportion(challenge)
        EquationTemplateId.EXPERT_STRUCTURED_SYSTEM_PAIR -> validateStructuredSystemPair(challenge)
        EquationTemplateId.EXPERT_STRUCTURED_QUADRATIC_ROOT_SET -> validateStructuredQuadraticRootSet(challenge)
        EquationTemplateId.EXPERT_STRUCTURED_QUADRATIC_INEQUALITY -> validateStructuredQuadraticInequality(challenge)
        EquationTemplateId.EXPERT_STRUCTURED_PARABOLA_VERTEX -> validateStructuredParabolaVertex(challenge)
        EquationTemplateId.EXTREME_RATIONAL_TWO_FRACTIONS -> validateExtremeRational(challenge)
        EquationTemplateId.EXTREME_NONLINEAR_SYSTEM -> validateNonlinearSystem(challenge)
        EquationTemplateId.EXTREME_CUBIC_FILTER -> validateCubic(challenge)
        EquationTemplateId.EXTREME_EXPONENTIAL_MIXED_SUM -> validateMixedExponential(challenge)
        EquationTemplateId.EXTREME_EXPONENTIAL_COMMON_BASES -> validateCommonBaseExponential(challenge)
        EquationTemplateId.EXTREME_LOG_THREE_FACTORS -> validateThreeLogs(challenge)
        EquationTemplateId.EXTREME_CRT_THREE -> validateCrt(challenge)
        EquationTemplateId.EXTREME_GCD_LCM_PAIR -> validateGcdLcm(challenge)
        EquationTemplateId.EXTREME_DIVISOR_COUNT -> validateDivisorCount(challenge)
        EquationTemplateId.EXTREME_LAST_TWO_DIGITS -> validateLastTwoDigits(challenge)
        EquationTemplateId.EXTREME_COMBINATION_SUM -> validateCombinationSum(challenge)
        EquationTemplateId.EXTREME_COMBINATION_PRODUCT -> validateCombinationProduct(challenge)
        EquationTemplateId.EXTREME_AFFINE_RECURRENCE -> validateRecurrence(challenge)
        EquationTemplateId.EXTREME_PARABOLA_MINIMUM -> validateParabolaMinimum(challenge)
        EquationTemplateId.EXTREME_POLYNOMIAL_DERIVATIVE -> validateDerivative(challenge)
        EquationTemplateId.EXTREME_RATIONAL_INEQUALITY_COUNT -> validateInequalityCount(challenge)
        EquationTemplateId.EXTREME_TRIG_INTEGER_ROOT_COUNT -> validateTrigCount(challenge)
        EquationTemplateId.EXTREME_LINEAR_CONGRUENCE -> validateCongruence(challenge)
        EquationTemplateId.EXTREME_ABSOLUTE_INTEGER_MINIMUM -> validateAbsoluteMinimum(challenge)
        EquationTemplateId.EXTREME_GEOMETRIC_SERIES_INVERSE -> validateGeometricSeries(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_RATIONAL_INEQUALITY -> validateStructuredRationalInequality(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_BIQUADRATIC_INEQUALITY -> validateStructuredBiquadraticInequality(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_NESTED_ABSOLUTE -> validateStructuredNestedAbsolute(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_PARAMETER_RANGE -> validateStructuredParameterRange(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_TRIG_ROOT_SET -> validateStructuredTrigRootSet(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_MODULAR_ROOT_SET -> validateStructuredModularRootSet(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_POLYNOMIAL_ROOT_SET -> validateStructuredPolynomialRootSet(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_LINE_INTERSECTION -> validateStructuredLineIntersection(challenge)
        EquationTemplateId.EXTREME_STRUCTURED_MIDPOINT -> validateStructuredMidpoint(challenge)
    }

    private fun validateExpertLinear(challenge: MathChallenge): Boolean {
        val v = match(challenge, Regex("""^(\d+)\(x \+ (\d+)\) \+ (\d+)\(x - (\d+)\) = (\d+)·x \+ (\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        return v[0] + v[2] != v[4] &&
            v[0] * (x + v[1]) + v[2] * (x - v[3]) == v[4] * x + v[5]
    }

    private fun validateExpertFraction(challenge: MathChallenge): Boolean {
        val (u, p, v, q, right) = match(challenge, Regex("""^\(x \+ (\d+)\) / (\d+) \+ \(x \+ (\d+)\) / (\d+) = (\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        return p > 0 && q > 0 && (x + u) * q + (x + v) * p == right * p * q
    }

    private fun validateExpertSystem(challenge: MathChallenge): Boolean {
        val v = match(challenge, Regex("""^(\d+)·x \+ (\d+)·y = (\d+)\n(\d+)·x \+ (\d+)·y = (\d+)\nx \+ y = \?$""")) ?: return false
        val determinant = v[0] * v[4] - v[1] * v[3]
        if (determinant == 0L) return false
        val xNumerator = v[2] * v[4] - v[1] * v[5]
        val yNumerator = v[0] * v[5] - v[2] * v[3]
        return xNumerator % determinant == 0L && yNumerator % determinant == 0L &&
            (xNumerator + yNumerator) / determinant == challenge.answer.toLong()
    }

    private fun validateExpertQuadratic(challenge: MathChallenge): Boolean {
        val (sum, product, lower) = match(challenge, Regex("""^x² - (\d+)·x \+ (\d+) = 0\nx > (\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        val other = sum - x
        return x * x - sum * x + product == 0L && other * x == product && x > lower && other <= lower
    }

    private fun validateExpertBiquadratic(challenge: MathChallenge): Boolean {
        val (sumSquares, productSquares, upper) = match(challenge, Regex("""^x⁴ - (\d+)·x² \+ (\d+) = 0\n0 < x < (\d+)$""")) ?: return false
        val valid = (1 until upper.toInt()).filter { candidate ->
            val square = candidate.toLong() * candidate
            square * square - sumSquares * square + productSquares == 0L
        }
        return valid == listOf(challenge.answer)
    }

    private fun validateExpertAbsolute(challenge: MathChallenge): Boolean {
        val (first, second, right, lower) = match(challenge, Regex("""^\|x - (\d+)\| \+ \|x - (\d+)\| = (\d+)\nx > (\d+)$""")) ?: return false
        val valid = ((lower + 1)..(lower + right + 2)).filter { x -> abs(x - first) + abs(x - second) == right }
        return valid == listOf(challenge.answer.toLong())
    }

    private fun validateExpertRadical(challenge: MathChallenge): Boolean {
        val (plus, minus, right) = match(challenge, Regex("""^√\(x \+ (\d+)\) \+ √\(x - (\d+)\) = (\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        val firstRoot = exactSquareRoot(x + plus) ?: return false
        val secondRoot = exactSquareRoot(x - minus) ?: return false
        return firstRoot + secondRoot == right
    }

    private fun validateExpertExponential(challenge: MathChallenge): Boolean {
        val (firstShift, secondShift, right) = match(challenge, Regex("""^2\^\(x - (\d+)\) \+ 2\^\(x - (\d+)\) = (\d+)$""")) ?: return false
        val x = challenge.answer
        if (x < firstShift || x < secondShift) return false
        return powLong(2, x - firstShift.toInt()) + powLong(2, x - secondShift.toInt()) == right
    }

    private fun validateExpertLogProduct(challenge: MathChallenge): Boolean {
        val (firstOffset, secondOffset, right) = match(challenge, Regex("""^log₂\(x - (\d+)\) \+ log₂\(x - (\d+)\) = (\d+)$""")) ?: return false
        val firstLog = exactLog2(challenge.answer.toLong() - firstOffset) ?: return false
        val secondLog = exactLog2(challenge.answer.toLong() - secondOffset) ?: return false
        return (firstLog + secondLog).toLong() == right
    }

    private fun validateExpertLogRatio(challenge: MathChallenge): Boolean {
        val (plus, minus, exponent) = match(challenge, Regex("""^log₂\(x \+ (\d+)\) - log₂\(x - (\d+)\) = (\d+)$""")) ?: return false
        val numerator = challenge.answer.toLong() + plus
        val denominator = challenge.answer.toLong() - minus
        return denominator > 0 && numerator == powLong(2, exponent.toInt()) * denominator
    }

    private fun validateArithmeticProgression(challenge: MathChallenge): Boolean {
        val (third, ninth) = match(challenge, Regex("""^a₃ = (\d+),  a₉ = (\d+)\naₙ₊₁ - aₙ = const\na₁₅ = \?$""")) ?: return false
        val delta = ninth - third
        return delta % 6L == 0L && third + 2L * delta == challenge.answer.toLong()
    }

    private fun validateGeometricProgression(challenge: MathChallenge): Boolean {
        val (second, fifth) = match(challenge, Regex("""^b₂ = (\d+),  b₅ = (\d+),  q > 0\nbₙ₊₁ / bₙ = q\nb₇ = \?$""")) ?: return false
        val ratio = (1..20).singleOrNull { second * powLong(it.toLong(), 3) == fifth } ?: return false
        return fifth * ratio * ratio == challenge.answer.toLong()
    }

    private fun validateDigitSystem(challenge: MathChallenge): Boolean {
        val m = Regex("""^a \+ b = (\d+)\na - b = (-?\d+)\na ∈ \{1…9},  b ∈ \{0…9}\n10a \+ b = \?$""").matchEntire(challenge.question) ?: return false
        val sum = m.groupValues[1].toInt()
        val difference = m.groupValues[2].toInt()
        if ((sum + difference) % 2 != 0) return false
        val a = (sum + difference) / 2
        val b = (sum - difference) / 2
        return a in 1..9 && b in 0..9 && 10 * a + b == challenge.answer
    }

    private fun validateRationalProportion(challenge: MathChallenge): Boolean {
        val (subtracted, added, numerator, denominator) = match(challenge, Regex("""^\(x - (\d+)\) / \(x \+ (\d+)\) = (\d+) / (\d+)\nx > 0$""")) ?: return false
        val x = challenge.answer.toLong()
        return denominator * (x - subtracted) == numerator * (x + added) && denominator != numerator
    }

    private fun validateStructuredSystemPair(challenge: MathChallenge): Boolean {
        val v = signedMatch(
            challenge,
            Regex("""^(\d+)·x \+ (\d+)·y = (-?\d+)\n(\d+)·x \+ (\d+)·y = (-?\d+)\n\(x; y\) = \?$""")
        ) ?: return false
        val spec = challenge.answerSpec as? MathAnswerSpec.OrderedPair ?: return false
        val x = spec.first.longValueExact()
        val y = spec.second.longValueExact()
        val determinant = v[0] * v[4] - v[1] * v[3]
        return determinant != 0L &&
            v[0] * x + v[1] * y == v[2] &&
            v[3] * x + v[4] * y == v[5] &&
            challenge.visual is ChallengeVisual.FunctionGraph
    }

    private fun validateStructuredQuadraticRootSet(challenge: MathChallenge): Boolean {
        val (sum, product) = match(
            challenge,
            Regex("""^x² - (\d+)·x \+ (\d+) = 0\n\{x ∈ ℝ} = \?$""")
        ) ?: return false
        val spec = challenge.answerSpec as? MathAnswerSpec.IntegerSet ?: return false
        val roots = (-100L..100L).filter { x -> x * x - sum * x + product == 0L }.toSet()
        return roots == spec.expected && roots.size == 2
    }

    private fun validateStructuredQuadraticInequality(challenge: MathChallenge): Boolean {
        val m = Regex("""^x² ([+-]) (\d+)·x ([+-]) (\d+) ≤ 0\nx ∈ \?$""")
            .matchEntire(challenge.question) ?: return false
        val coefficient = signedValue(m.groupValues[1], m.groupValues[2])
        val constant = signedValue(m.groupValues[3], m.groupValues[4])
        val spec = challenge.answerSpec as? MathAnswerSpec.IntervalSet ?: return false
        val interval = spec.expected.singleOrNull() ?: return false
        val lower = finiteValue(interval.lower) ?: return false
        val upper = finiteValue(interval.upper) ?: return false
        return interval.lowerInclusive && interval.upperInclusive &&
            coefficient == -(lower + upper) && constant == lower * upper &&
            challenge.visual is ChallengeVisual.NumberLine
    }

    private fun validateStructuredParabolaVertex(challenge: MathChallenge): Boolean {
        val (a, linear, constant) = match(
            challenge,
            Regex("""^y = (\d+)·x² - (\d+)·x \+ (\d+)\nvertex \(x; y\) = \?$""")
        ) ?: return false
        val spec = challenge.answerSpec as? MathAnswerSpec.OrderedPair ?: return false
        val x = spec.first.longValueExact()
        val y = spec.second.longValueExact()
        return 2L * a * x == linear && a * x * x - linear * x + constant == y &&
            challenge.visual is ChallengeVisual.FunctionGraph
    }

    private fun validateExtremeRational(challenge: MathChallenge): Boolean {
        val (n1, p, n2, q, right) = match(challenge, Regex("""^(\d+) / \(x \+ (\d+)\) \+ (\d+) / \(x \+ (\d+)\) = (\d+)\nx > 0$""")) ?: return false
        val valid = (1L..500L).filter { x -> n1 * (x + q) + n2 * (x + p) == right * (x + p) * (x + q) }
        return valid == listOf(challenge.answer.toLong())
    }

    private fun validateNonlinearSystem(challenge: MathChallenge): Boolean {
        val (sum, product) = match(challenge, Regex("""^x \+ y = (\d+)\nx·y = (\d+)\nx > y\nx = \?$""")) ?: return false
        val x = challenge.answer.toLong()
        val y = sum - x
        return x > y && x * y == product
    }

    private fun validateCubic(challenge: MathChallenge): Boolean {
        val (sum, pairs, product, lower) = match(challenge, Regex("""^x³ - (\d+)·x² \+ (\d+)·x - (\d+) = 0\nx > (\d+)$""")) ?: return false
        val valid = ((lower + 1)..100L).filter { x -> x * x * x - sum * x * x + pairs * x - product == 0L }
        return valid == listOf(challenge.answer.toLong())
    }

    private fun validateMixedExponential(challenge: MathChallenge): Boolean {
        val (firstShift, secondShift, right) = match(challenge, Regex("""^2\^\(x - (\d+)\) \+ 4\^\(x - (\d+)\) = (\d+)$""")) ?: return false
        val x = challenge.answer
        if (x < firstShift || x < secondShift) return false
        return powLong(2, x - firstShift.toInt()) + powLong(4, x - secondShift.toInt()) == right
    }

    private fun validateCommonBaseExponential(challenge: MathChallenge): Boolean {
        val (firstShift, secondShift, extra) = match(challenge, Regex("""^8\^\(x - (\d+)\) = 4\^\(x - (\d+)\) · 2\^(\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        return 3L * (x - firstShift) == 2L * (x - secondShift) + extra
    }

    private fun validateThreeLogs(challenge: MathChallenge): Boolean {
        val (first, second, third, right) = match(challenge, Regex("""^log₂\(x - (\d+)\) \+ log₂\(x - (\d+)\) \+\nlog₂\(x - (\d+)\) = (\d+)$""")) ?: return false
        val x = challenge.answer.toLong()
        val logs = listOf(x - first, x - second, x - third).map { exactLog2(it) ?: return false }
        return logs.sum().toLong() == right
    }

    private fun validateCrt(challenge: MathChallenge): Boolean {
        val v = match(challenge, Regex("""^n ≡ (\d+) \(mod (\d+)\)\nn ≡ (\d+) \(mod (\d+)\)\nn ≡ (\d+) \(mod (\d+)\)\nmin\(n > 0\) = \?$""")) ?: return false
        fun matches(n: Long) = n % v[1] == v[0] && n % v[3] == v[2] && n % v[5] == v[4]
        val answer = challenge.answer.toLong()
        return matches(answer) && (1L until answer).none(::matches)
    }

    private fun validateGcdLcm(challenge: MathChallenge): Boolean {
        val (gcdValue, lcmValue, sum) = match(challenge, Regex("""^gcd\(a, b\) = (\d+)\nlcm\(a, b\) = (\d+)\na \+ b = (\d+),  a < b\nb = \?$""")) ?: return false
        val valid = (1L until sum).filter { b ->
            val a = sum - b
            a < b && gcd(a, b) == gcdValue && lcm(a, b) == lcmValue
        }
        return valid == listOf(challenge.answer.toLong())
    }

    private fun validateDivisorCount(challenge: MathChallenge): Boolean {
        val m = Regex("""^\|\{d ∈ ℕ : d \| (\d+)}\| = \?$""").matchEntire(challenge.question) ?: return false
        return countPositiveDivisors(m.groupValues[1].toLong()) == challenge.answer.toLong()
    }

    private fun validateLastTwoDigits(challenge: MathChallenge): Boolean {
        val (base, exponent) = match(challenge, Regex("""^(\d+)\^(\d+) mod 100 = \?$""")) ?: return false
        return modularPower(base, exponent, 100) == challenge.answer
    }

    private fun validateCombinationSum(challenge: MathChallenge): Boolean {
        val right = match(challenge, Regex("""^C\(x, 2\) \+ C\(x, 3\) = (\d+)\nx ∈ ℕ,  x ≥ 3$"""))?.singleOrNull() ?: return false
        return (3..60).filter { combination(it, 2) + combination(it, 3) == right } == listOf(challenge.answer)
    }

    private fun validateCombinationProduct(challenge: MathChallenge): Boolean {
        val right = match(challenge, Regex("""^C\(x, 2\) · \(x - 2\) = (\d+)\nx ∈ ℕ,  x ≥ 3$"""))?.singleOrNull() ?: return false
        return (3..60).filter { combination(it, 2) * (it - 2) == right } == listOf(challenge.answer)
    }

    private fun validateRecurrence(challenge: MathChallenge): Boolean {
        val (first, multiplier, increment) = match(challenge, Regex("""^a₁ = (\d+)\naₙ₊₁ = (\d+)·aₙ \+ (\d+)\na₅ = \?$""")) ?: return false
        var value = first
        repeat(4) { value = multiplier * value + increment }
        return value == challenge.answer.toLong()
    }

    private fun validateParabolaMinimum(challenge: MathChallenge): Boolean {
        val (a, linear, constant) = match(challenge, Regex("""^f\(x\) = (\d+)·x² - (\d+)·x \+ (\d+)\nmin\(x ∈ ℝ\) f\(x\) = \?$""")) ?: return false
        val denominator = 4L * a
        val numerator = linear * linear
        return numerator % denominator == 0L && constant - numerator / denominator == challenge.answer.toLong()
    }

    private fun validateDerivative(challenge: MathChallenge): Boolean {
        val (cubic, quadratic, linear, _, point) = match(challenge, Regex("""^f\(x\) = (\d+)·x³ \+ (\d+)·x² \+ (\d+)·x \+ (\d+)\nf′\((\d+)\) = \?$""")) ?: return false
        return 3L * cubic * point * point + 2L * quadratic * point + linear == challenge.answer.toLong()
    }

    private fun validateInequalityCount(challenge: MathChallenge): Boolean {
        val m = Regex("""^\(\(x - (\(?-?\d+\)?)\)\(x - (\(?-?\d+\)?)\)\) / \(x - (\(?-?\d+\)?)\) > 0\nx ∈ ℤ ∩ \[(-?\d+); (-?\d+)]\nnumber of x = \?$""").matchEntire(challenge.question) ?: return false
        val first = parseWrappedLong(m.groupValues[1])
        val second = parseWrappedLong(m.groupValues[2])
        val pole = parseWrappedLong(m.groupValues[3])
        val lower = m.groupValues[4].toLong()
        val upper = m.groupValues[5].toLong()
        val count = (lower..upper).count { x ->
            x != pole && ((x - first) * (x - second)).compareTo(0L) * (x - pole).compareTo(0L) > 0
        }
        return count == challenge.answer
    }

    private fun validateTrigCount(challenge: MathChallenge): Boolean {
        val upper = match(challenge, Regex("""^sin\(π·x / 6\) = 1 / 2\nx ∈ ℤ ∩ \[0; (\d+)]\nnumber of x = \?$"""))?.singleOrNull()?.toInt() ?: return false
        return (0..upper).count { it % 12 == 1 || it % 12 == 5 } == challenge.answer
    }

    private fun validateCongruence(challenge: MathChallenge): Boolean {
        val v = match(challenge, Regex("""^(\d+)·x ≡ (\d+) \(mod (\d+)\)\n1 ≤ x < (\d+)$""")) ?: return false
        if (v[2] != v[3]) return false
        val valid = (1L until v[2]).filter { x -> v[0] * x % v[2] == v[1] }
        return valid == listOf(challenge.answer.toLong())
    }

    private fun validateAbsoluteMinimum(challenge: MathChallenge): Boolean {
        val (a, b, c, d) = match(challenge, Regex("""^min\(x ∈ ℤ ∩ \[0; 50]\)\n\|(\d+)·x - (\d+)\| \+ \|(\d+)·x - (\d+)\| = \?$""")) ?: return false
        return (0L..50L).minOf { x -> abs(a * x - b) + abs(c * x - d) } == challenge.answer.toLong()
    }

    private fun validateGeometricSeries(challenge: MathChallenge): Boolean {
        val right = match(challenge, Regex("""^1 \+ x \+ x² \+ x³ \+ x⁴ = (\d+)\nx ∈ ℕ,  x > 1$"""))?.singleOrNull() ?: return false
        return (2..20).filter { x -> (0..4).sumOf { powLong(x.toLong(), it) } == right } == listOf(challenge.answer)
    }

    private fun validateStructuredRationalInequality(challenge: MathChallenge): Boolean {
        val m = Regex("""^\(x ([+-]) (\d+)\) / \(x ([+-]) (\d+)\) > 0\nx ∈ \?$""")
            .matchEntire(challenge.question) ?: return false
        val zero = -signedValue(m.groupValues[1], m.groupValues[2])
        val pole = -signedValue(m.groupValues[3], m.groupValues[4])
        val spec = challenge.answerSpec as? MathAnswerSpec.IntervalSet ?: return false
        val intervals = spec.expected
        if (intervals.size != 2) return false
        val first = intervals[0]
        val second = intervals[1]
        return first.lower === MathBoundary.NegativeInfinity && finiteValue(first.upper) == zero &&
            !first.upperInclusive && finiteValue(second.lower) == pole &&
            second.upper === MathBoundary.PositiveInfinity && !second.lowerInclusive &&
            challenge.visual is ChallengeVisual.NumberLine
    }

    private fun validateStructuredBiquadraticInequality(challenge: MathChallenge): Boolean {
        val (sumSquares, productSquares) = match(
            challenge,
            Regex("""^x⁴ - (\d+)·x² \+ (\d+) ≤ 0\nx ∈ \?$""")
        ) ?: return false
        val spec = challenge.answerSpec as? MathAnswerSpec.IntervalSet ?: return false
        if (spec.expected.size != 2) return false
        val left = spec.expected[0]
        val right = spec.expected[1]
        val outer = finiteValue(right.upper) ?: return false
        val inner = finiteValue(right.lower) ?: return false
        return listOf(left, right).all { it.lowerInclusive && it.upperInclusive } &&
            finiteValue(left.lower) == -outer && finiteValue(left.upper) == -inner &&
            inner * inner + outer * outer == sumSquares &&
            inner * inner * outer * outer == productSquares &&
            challenge.visual is ChallengeVisual.NumberLine
    }

    private fun validateStructuredNestedAbsolute(challenge: MathChallenge): Boolean {
        val m = Regex("""^\|\|x ([+-]) (\d+)\| - (\d+)\| ≤ (\d+)\nx ∈ \?$""")
            .matchEntire(challenge.question) ?: return false
        val center = -signedValue(m.groupValues[1], m.groupValues[2])
        val radius = m.groupValues[3].toLong()
        val tolerance = m.groupValues[4].toLong()
        val spec = challenge.answerSpec as? MathAnswerSpec.IntervalSet ?: return false
        if (spec.expected.size != 2 || radius <= tolerance) return false
        val near = radius - tolerance
        val far = radius + tolerance
        val expectedBounds = listOf(
            center - far to center - near,
            center + near to center + far
        )
        return spec.expected.zip(expectedBounds).all { (interval, bounds) ->
            finiteValue(interval.lower) == bounds.first && finiteValue(interval.upper) == bounds.second &&
                interval.lowerInclusive && interval.upperInclusive
        }
    }

    private fun validateStructuredParameterRange(challenge: MathChallenge): Boolean {
        val m = Regex("""^x² - 2m·x ([+-]) (\d+)·m ([+-]) (\d+) = 0\n∃x ∈ ℝ;  m ∈ \?$""")
            .matchEntire(challenge.question) ?: return false
        val sum = signedValue(m.groupValues[1], m.groupValues[2])
        val negativeProduct = signedValue(m.groupValues[3], m.groupValues[4])
        val product = -negativeProduct
        val spec = challenge.answerSpec as? MathAnswerSpec.IntervalSet ?: return false
        if (spec.expected.size != 2) return false
        val lower = finiteValue(spec.expected[0].upper) ?: return false
        val upper = finiteValue(spec.expected[1].lower) ?: return false
        return spec.expected[0].lower === MathBoundary.NegativeInfinity &&
            spec.expected[1].upper === MathBoundary.PositiveInfinity &&
            spec.expected[0].upperInclusive && spec.expected[1].lowerInclusive &&
            lower + upper == sum && lower * upper == product &&
            challenge.visual is ChallengeVisual.NumberLine
    }

    private fun validateStructuredTrigRootSet(challenge: MathChallenge): Boolean {
        val upper = match(
            challenge,
            Regex("""^sin\(π·x / 6\) = 1 / 2\nx ∈ ℤ ∩ \[0; (\d+)]\n\{x} = \?$""")
        )?.singleOrNull()?.toInt() ?: return false
        val expected = (0..upper).filter { it % 12 == 1 || it % 12 == 5 }.map(Int::toLong).toSet()
        return (challenge.answerSpec as? MathAnswerSpec.IntegerSet)?.expected == expected
    }

    private fun validateStructuredModularRootSet(challenge: MathChallenge): Boolean {
        val (remainder, modulus, upper) = match(
            challenge,
            Regex("""^x² ≡ (\d+) \(mod (\d+)\)\nx ∈ \{0…(\d+)}\n\{x} = \?$""")
        ) ?: return false
        if (upper != modulus - 1L) return false
        val expected = (0L until modulus).filter { x -> x * x % modulus == remainder }.toSet()
        return expected.size == 2 && (challenge.answerSpec as? MathAnswerSpec.IntegerSet)?.expected == expected
    }

    private fun validateStructuredPolynomialRootSet(challenge: MathChallenge): Boolean {
        val (sum, pairs, product) = match(
            challenge,
            Regex("""^x³ - (\d+)·x² \+ (\d+)·x - (\d+) = 0\n\{x ∈ ℝ} = \?$""")
        ) ?: return false
        val roots = (1L..100L).filter { x ->
            x * x * x - sum * x * x + pairs * x - product == 0L
        }.toSet()
        return roots.size == 3 && (challenge.answerSpec as? MathAnswerSpec.IntegerSet)?.expected == roots
    }

    private fun validateStructuredLineIntersection(challenge: MathChallenge): Boolean {
        val m = Regex("""^y = (-?\d+)·x ([+-]) (\d+)\ny = (-?\d+)·x ([+-]) (\d+)\nintersection \(x; y\) = \?$""")
            .matchEntire(challenge.question) ?: return false
        val firstSlope = m.groupValues[1].toLong()
        val firstIntercept = signedValue(m.groupValues[2], m.groupValues[3])
        val secondSlope = m.groupValues[4].toLong()
        val secondIntercept = signedValue(m.groupValues[5], m.groupValues[6])
        val spec = challenge.answerSpec as? MathAnswerSpec.OrderedPair ?: return false
        val x = spec.first.longValueExact()
        val y = spec.second.longValueExact()
        return firstSlope != secondSlope &&
            firstSlope * x + firstIntercept == y &&
            secondSlope * x + secondIntercept == y &&
            challenge.visual is ChallengeVisual.FunctionGraph
    }

    private fun validateStructuredMidpoint(challenge: MathChallenge): Boolean {
        val v = signedMatch(
            challenge,
            Regex("""^A\((-?\d+); (-?\d+)\),  B\((-?\d+); (-?\d+)\)\nM = midpoint\(A, B\)\nM\(x; y\) = \?$""")
        ) ?: return false
        val spec = challenge.answerSpec as? MathAnswerSpec.OrderedPair ?: return false
        val x = spec.first.longValueExact()
        val y = spec.second.longValueExact()
        val visual = challenge.visual as? ChallengeVisual.GeometryDiagram ?: return false
        return v[0] + v[2] == 2L * x && v[1] + v[3] == 2L * y &&
            visual.points.size >= 3 && visual.segments.isNotEmpty()
    }

    private fun assertValidAnswerShape(challenge: MathChallenge) {
        when (val spec = challenge.answerSpec) {
            is MathAnswerSpec.Integer -> {
                assertTrue(spec.expected in 1L..9_999L)
                assertEquals(spec.expected, challenge.answer.toLong())
            }
            is MathAnswerSpec.IntegerSet -> {
                assertTrue("Generated finite set must not be empty", spec.expected.isNotEmpty())
                assertTrue(spec.expected.all { it in -100_000L..100_000L })
                assertEquals(0, challenge.answer)
            }
            is MathAnswerSpec.IntervalSet -> {
                assertTrue(spec.expected.isNotEmpty())
                assertEquals(0, challenge.answer)
            }
            is MathAnswerSpec.OrderedPair -> {
                assertTrue(spec.first.abs() <= 100_000.toBigDecimal())
                assertTrue(spec.second.abs() <= 100_000.toBigDecimal())
                assertEquals(0, challenge.answer)
            }
        }
        challenge.visual?.let(::assertValidVisual)
    }

    private fun assertValidVisual(visual: ChallengeVisual) {
        assertTrue(visual.contentDescription.isNotBlank())
        when (visual) {
            is ChallengeVisual.FunctionGraph -> {
                assertTrue(visual.xMin < visual.xMax && visual.yMin < visual.yMax)
                assertTrue(visual.series.isNotEmpty())
                assertTrue(visual.series.all { series ->
                    series.points.size >= 2 && series.points.all { it.x.isFinite() && it.y.isFinite() }
                })
            }
            is ChallengeVisual.NumberLine -> {
                assertTrue(visual.min < visual.max)
                assertTrue(visual.intervals.isNotEmpty())
            }
            is ChallengeVisual.GeometryDiagram -> {
                assertTrue(visual.points.size >= 3)
                assertTrue(visual.segments.isNotEmpty())
                assertTrue(visual.points.all { it.x.isFinite() && it.y.isFinite() })
            }
        }
    }

    private fun finiteValue(boundary: MathBoundary): Long? =
        (boundary as? MathBoundary.Finite)?.value?.longValueExact()

    private fun signedValue(sign: String, magnitude: String): Long =
        magnitude.toLong() * if (sign == "-") -1L else 1L

    private fun signedMatch(challenge: MathChallenge, regex: Regex): List<Long>? =
        regex.matchEntire(challenge.question)?.groupValues?.drop(1)?.map(String::toLong)

    private fun match(challenge: MathChallenge, regex: Regex): List<Long>? =
        regex.matchEntire(challenge.question)?.groupValues?.drop(1)?.map { it.toLong() }

    private fun assertNoForbiddenCalculus(question: String) {
        val normalized = question.lowercase()
        assertTrue("Differential notation is forbidden: $question", "d/d" !in normalized)
        assertTrue("Differential equation notation is forbidden: $question", "dy" !in normalized)
        assertTrue("Integral tasks are forbidden: $question", '∫' !in question)
        assertTrue("Differential equations are forbidden: $question", "y′ =" !in normalized)
    }

    private fun exactSquareRoot(value: Long): Long? {
        if (value < 0L) return null
        var root = 0L
        while (root * root < value) root += 1
        return root.takeIf { it * it == value }
    }

    private fun exactLog2(value: Long): Int? {
        if (value <= 0L || value and (value - 1L) != 0L) return null
        return java.lang.Long.numberOfTrailingZeros(value)
    }

    private fun powLong(base: Long, exponent: Int): Long {
        var result = 1L
        repeat(exponent) { result = Math.multiplyExact(result, base) }
        return result
    }

    private fun modularPower(base: Long, exponent: Long, modulus: Long): Int {
        var factor = base % modulus
        var remaining = exponent
        var result = 1L
        while (remaining > 0) {
            if (remaining and 1L == 1L) result = result * factor % modulus
            factor = factor * factor % modulus
            remaining = remaining ushr 1
        }
        return result.toInt()
    }

    private fun combination(n: Int, k: Int): Long {
        val adjusted = minOf(k, n - k)
        var result = 1L
        for (index in 1..adjusted) result = result * (n - adjusted + index) / index
        return result
    }

    private fun gcd(first: Long, second: Long): Long {
        var a = abs(first)
        var b = abs(second)
        while (b != 0L) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a
    }

    private fun lcm(first: Long, second: Long): Long = first / gcd(first, second) * second

    private fun countPositiveDivisors(number: Long): Long {
        var remaining = number
        var candidate = 2L
        var result = 1L
        while (candidate * candidate <= remaining) {
            var exponent = 0
            while (remaining % candidate == 0L) {
                exponent += 1
                remaining /= candidate
            }
            if (exponent > 0) result *= exponent + 1L
            candidate += 1
        }
        if (remaining > 1L) result *= 2L
        return result
    }

    private fun parseWrappedLong(value: String): Long = value.removePrefix("(").removeSuffix(")").toLong()

    private companion object {
        const val SAMPLES_PER_TEMPLATE = 250
    }
}
