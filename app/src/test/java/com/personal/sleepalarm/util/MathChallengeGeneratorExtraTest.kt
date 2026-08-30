package com.personal.sleepalarm.util

import com.personal.sleepalarm.domain.model.MathDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Тесты расширенного генератора задач (F4): деление и скобки.
 *
 * Подход: генератор стохастический, поэтому проверяем не конкретные числа,
 * а ИНВАРИАНТЫ на большом числе прогонов с фиксированным seed:
 * 1. answer всегда совпадает с независимым пересчётом по тексту question;
 * 2. answer всегда целое неотрицательное;
 * 3. деление целочисленное (следует из пересчёта, но проверяем явно);
 * 4. новые операторы реально достижимы в MEDIUM/HARD;
 * 5. EASY не содержит деления и скобок.
 */
class MathChallengeGeneratorExtraTest {

    private val regexAdd = Regex("""^(-?\d+) \+ (-?\d+)$""")
    private val regexSub = Regex("""^(-?\d+) - (-?\d+)$""")
    private val regexMul = Regex("""^(-?\d+) \* (-?\d+)$""")
    private val regexDiv = Regex("""^(-?\d+) / (-?\d+)$""")
    private val regexParenAdd = Regex("""^\((-?\d+) \+ (-?\d+)\) \* (-?\d+)$""")
    private val regexParenSub = Regex("""^\((-?\d+) - (-?\d+)\) \* (-?\d+)$""")

    /**
     * Независимый вычислитель для всех поддерживаемых форматов вопроса.
     * Возвращает null, если формат не распознан (тогда тест упадёт — это баг).
     */
    private fun evaluate(question: String): Int? {
        regexParenAdd.matchEntire(question)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val c = m.groupValues[3].toInt()
            return (a + b) * c
        }
        regexParenSub.matchEntire(question)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val c = m.groupValues[3].toInt()
            return (a - b) * c
        }
        regexAdd.matchEntire(question)?.let { m ->
            return m.groupValues[1].toInt() + m.groupValues[2].toInt()
        }
        regexSub.matchEntire(question)?.let { m ->
            return m.groupValues[1].toInt() - m.groupValues[2].toInt()
        }
        regexMul.matchEntire(question)?.let { m ->
            return m.groupValues[1].toInt() * m.groupValues[2].toInt()
        }
        regexDiv.matchEntire(question)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            return a / b // целочисленное, как в задаче
        }
        return null
    }

    private fun isDivision(question: String) = '/' in question
    private fun isParen(question: String) = '(' in question

    @Test
    fun `every generated answer matches independent evaluation`() {
        val random = Random(42)

        for (difficulty in listOf(
            MathDifficulty.EASY,
            MathDifficulty.MEDIUM,
            MathDifficulty.HARD
        )) {
            repeat(300) {
                val challenge = MathChallengeGenerator.generate(difficulty, random)
                val expected = evaluate(challenge.question)

                assertNotNull(
                    "нераспознанный формат вопроса: ${challenge.question}",
                    expected
                )
                assertEquals(
                    "ответ не совпадает с пересчётом для ${challenge.question}",
                    expected,
                    challenge.answer
                )
                assertTrue(
                    "ответ должен быть неотрицательным: ${challenge.question} = ${challenge.answer}",
                    challenge.answer >= 0
                )
            }
        }
    }

    @Test
    fun `division is integer and divisor is nonzero`() {
        val random = Random(7)
        var seenDivision = 0

        repeat(500) {
            val challenge = MathChallengeGenerator.generate(MathDifficulty.HARD, random)
            if (isDivision(challenge.question)) {
                seenDivision++
                val m = regexDiv.matchEntire(challenge.question)
                assertNotNull("деление не распознано: ${challenge.question}", m)
                val a = m!!.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()

                assertTrue("делитель не должен быть нулём", b != 0)
                assertEquals("деление должно быть целочисленным", 0, a % b)
                assertTrue("делимое неотрицательно", a >= 0)
            }
        }

        assertTrue("деление должно встречаться в HARD", seenDivision > 0)
    }

    @Test
    fun `hard reaches both division and parentheses`() {
        val random = Random(123)
        val types = mutableSetOf<String>()

        repeat(500) {
            val q = MathChallengeGenerator.generate(MathDifficulty.HARD, random).question
            when {
                isParen(q) -> types += "paren"
                isDivision(q) -> types += "div"
            }
        }

        assertTrue("скобки должны быть достижимы в HARD", "paren" in types)
        assertTrue("деление должно быть достижимо в HARD", "div" in types)
    }

    @Test
    fun `medium reaches division`() {
        val random = Random(999)
        var seenDivision = false

        repeat(500) {
            val q = MathChallengeGenerator.generate(MathDifficulty.MEDIUM, random).question
            if (isDivision(q)) seenDivision = true
        }

        assertTrue("деление должно быть достижимо в MEDIUM", seenDivision)
    }

    @Test
    fun `easy never uses division or parentheses`() {
        val random = Random(2026)

        repeat(500) {
            val q = MathChallengeGenerator.generate(MathDifficulty.EASY, random).question
            assertTrue("EASY не должен содержать деление: $q", !isDivision(q))
            assertTrue("EASY не должен содержать скобки: $q", !isParen(q))
        }
    }

    @Test
    fun `parentheses expressions keep positive inner difference`() {
        // Для (a - b) * c генератор гарантирует a > b >= 1, c >= 2 → ответ >= 2.
        val random = Random(55)

        repeat(500) {
            val q = MathChallengeGenerator.generate(MathDifficulty.HARD, random).question
            val m = regexParenSub.matchEntire(q)
            if (m != null) {
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val c = m.groupValues[3].toInt()
                assertTrue("a должно быть больше b", a > b)
                assertTrue("b >= 1", b >= 1)
                assertTrue("c >= 2", c >= 2)
                assertTrue("ответ >= 2", (a - b) * c >= 2)
            }
        }
    }
}
