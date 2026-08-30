package com.personal.sleepalarm.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathAnswerParserTest {

    @Test
    fun `legacy integer accepts signs and canonicalizes leading zeroes`() {
        val spec = MathAnswerSpec.Integer(-12)

        assertCorrect(spec, "  -0012 ", "-12")
        assertIncorrect(spec, "12")
        assertInvalid(spec, "12.0", MathAnswerParseError.INVALID_INTEGER)
    }

    @Test
    fun `integer set ignores order duplicates braces and separator style`() {
        val spec = MathAnswerSpec.IntegerSet(setOf(-3, 0, 7))

        assertCorrect(spec, "{7; -3; 0; 7}", "{-3; 0; 7}")
        assertCorrect(spec, " 0,7,-3 ", "{-3; 0; 7}")
        assertIncorrect(spec, "{-3; 0}")
        assertInvalid(spec, "{-3;;7}", MathAnswerParseError.INVALID_SET)
    }

    @Test
    fun `empty integer set has canonical empty-set symbol`() {
        val spec = MathAnswerSpec.IntegerSet(emptySet())

        assertCorrect(spec, "{}", "∅")
        assertCorrect(spec, "∅", "∅")
    }

    @Test
    fun `interval accepts x membership prefix infinity and U union`() {
        val spec = MathAnswerSpec.IntervalSet(
            listOf(
                MathInterval.atMost(-2, inclusive = true),
                MathInterval.atLeast(5, inclusive = false)
            )
        )

        assertCorrect(spec, "x∈(-∞; -2] U (5; +∞)", "x ∈ (-∞; -2] ∪ (5; +∞)")
        assertCorrect(spec, "X = (-inf;-2]∪(5;inf)", "x ∈ (-∞; -2] ∪ (5; +∞)")
        assertIncorrect(spec, "(-∞;-2) ∪ (5;+∞)")
    }

    @Test
    fun `touching intervals merge when either touching endpoint is included`() {
        val spec = MathAnswerSpec.IntervalSet(listOf(MathInterval.closed(0, 2)))

        assertCorrect(spec, "[0;1) ∪ [1;2]", "x ∈ [0; 2]")
        assertCorrect(spec, "[1;2] U [0;1]", "x ∈ [0; 2]")

        val punctured = MathAnswerSpec.IntervalSet(
            listOf(MathInterval.open(0, 1), MathInterval.open(1, 2))
        )
        assertCorrect(punctured, "(1;2) ∪ (0;1)", "x ∈ (0; 1) ∪ (1; 2)")
        assertIncorrect(punctured, "(0;2)")
    }

    @Test
    fun `overlapping intervals and decimal spellings normalize exactly`() {
        val spec = MathAnswerSpec.IntervalSet(
            listOf(
                MathInterval(
                    MathBoundary.Finite(BigDecimal("0.5")),
                    MathBoundary.Finite(BigDecimal("3")),
                    lowerInclusive = true,
                    upperInclusive = false
                )
            )
        )

        assertCorrect(spec, "[0,50; 2] ∪ (2; 3)", "x ∈ [0.5; 3)")
        assertInvalid(spec, "[3; 1]", MathAnswerParseError.INVALID_INTERVAL)
        assertInvalid(spec, "[-∞; 1]", MathAnswerParseError.INVALID_INTERVAL)
    }

    @Test
    fun `point interval joins an adjacent open interval without losing the point`() {
        val spec = MathAnswerSpec.IntervalSet(
            listOf(
                MathInterval(
                    lower = MathBoundary.Finite(1),
                    upper = MathBoundary.Finite(2),
                    lowerInclusive = true,
                    upperInclusive = false
                )
            )
        )

        assertCorrect(spec, "[1;1] ∪ (1;2)", "x ∈ [1; 2)")
        assertInvalid(spec, "(1;1)", MathAnswerParseError.INVALID_INTERVAL)
    }

    @Test
    fun `interval union merges across infinity-adjacent pieces`() {
        val spec = MathAnswerSpec.IntervalSet(
            listOf(
                MathInterval(
                    lower = MathBoundary.NegativeInfinity,
                    upper = MathBoundary.Finite(3),
                    upperInclusive = false
                )
            )
        )

        assertCorrect(spec, "(-∞;1] U (1;3)", "x ∈ (-∞; 3)")
    }

    @Test
    fun `ordered pair is order sensitive and accepts decimal comma with semicolon`() {
        val spec = MathAnswerSpec.OrderedPair(BigDecimal("-1.5"), BigDecimal("2"))

        assertCorrect(spec, "(-1,50; +2.0)", "(-1.5; 2)")
        assertIncorrect(spec, "(2; -1.5)")
        assertInvalid(spec, "(-1.5; 2; 3)", MathAnswerParseError.INVALID_PAIR)
    }

    @Test
    fun `canonical expected answer is stable`() {
        assertEquals("-8", MathAnswerParser.canonical(MathAnswerSpec.Integer(-8)))
        assertEquals("{-2; 1; 9}", MathAnswerParser.canonical(MathAnswerSpec.IntegerSet(setOf(9, -2, 1))))
        assertEquals("(0; 2.25)", MathAnswerParser.canonical(MathAnswerSpec.OrderedPair(BigDecimal("-0.0"), BigDecimal("2.250"))))
    }

    @Test
    fun `input sanitizer keeps partial mathematical notation and caps length`() {
        val sanitized = MathAnswerParser.sanitizeInput("x ∈ (-∞; 2] ∪ [5; +∞) DROP TABLE", maxLength = 32)

        assertTrue(sanitized.startsWith("x ∈ (-∞; 2] ∪ [5; +∞)"))
        assertFalse(sanitized.contains("DROP"))
        assertTrue(sanitized.length <= 32)
    }

    private fun assertCorrect(spec: MathAnswerSpec, input: String, canonical: String? = null) {
        val result = MathAnswerParser.validate(spec, input)
        assertTrue("Expected correct but was $result", result is MathAnswerValidation.Correct)
        if (canonical != null) assertEquals(canonical, (result as MathAnswerValidation.Correct).canonicalInput)
    }

    private fun assertIncorrect(spec: MathAnswerSpec, input: String) {
        assertTrue(MathAnswerParser.validate(spec, input) is MathAnswerValidation.Incorrect)
    }

    private fun assertInvalid(spec: MathAnswerSpec, input: String, error: MathAnswerParseError) {
        assertEquals(MathAnswerValidation.Invalid(error), MathAnswerParser.validate(spec, input))
    }
}
