package com.personal.sleepalarm.util

import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathDifficulty
import kotlin.random.Random

/**
 * Генератор математических задач для экрана будильника.
 *
 * ДОБАВЛЕНО (F4):
 * - целочисленное деление (MEDIUM, HARD);
 * - выражения со скобками (HARD).
 *
 * Существующие ветки (EASY: +, -; MEDIUM: +, -, *; HARD: +, -, *) НЕ удалены.
 * Ответ ВСЕГДА целое неотрицательное число.
 */
object MathChallengeGenerator {

    fun generate(
        difficulty: MathDifficulty,
        random: Random = Random.Default
    ): MathChallenge {
        return when (difficulty) {
            MathDifficulty.EASY -> generateEasy(random)
            MathDifficulty.MEDIUM -> generateMedium(random)
            MathDifficulty.HARD -> generateHard(random)
        }
    }

    // === EASY: только + и - (НЕ менять) ===

    private fun generateEasy(random: Random): MathChallenge {
        return when (random.nextInt(2)) {
            0 -> {
                val a = random.nextInt(5, 21)
                val b = random.nextInt(5, 21)
                val answer = a + b

                MathChallenge(
                    question = "$a + $b",
                    answer = answer,
                    difficulty = MathDifficulty.EASY
                )
            }

            else -> {
                val a = random.nextInt(10, 31)
                val b = random.nextInt(5, a)
                val answer = a - b

                MathChallenge(
                    question = "$a - $b",
                    answer = answer,
                    difficulty = MathDifficulty.EASY
                )
            }
        }
    }

    // === MEDIUM: +, -, *, деление (ДОБАВЛЕНО: деление) ===

    private fun generateMedium(random: Random): MathChallenge {
        // ДОБАВЛЕНО: 4 ветки вместо 3 (nextInt(4))
        return when (random.nextInt(4)) {
            0 -> {
                val a = random.nextInt(12, 50)
                val b = random.nextInt(10, 50)
                val answer = a + b

                MathChallenge(
                    question = "$a + $b",
                    answer = answer,
                    difficulty = MathDifficulty.MEDIUM
                )
            }

            1 -> {
                val a = random.nextInt(20, 81)
                val b = random.nextInt(5, a)
                val answer = a - b

                MathChallenge(
                    question = "$a - $b",
                    answer = answer,
                    difficulty = MathDifficulty.MEDIUM
                )
            }

            2 -> {
                val a = random.nextInt(3, 10)
                val b = random.nextInt(4, 10)
                val answer = a * b

                MathChallenge(
                    question = "$a * $b",
                    answer = answer,
                    difficulty = MathDifficulty.MEDIUM
                )
            }

            // ДОБАВЛЕНО: целочисленное деление
            else -> {
                // Генерируем «снизу вверх»: делитель b и частное answer,
                // затем делимое a = b * answer.
                // Гарантированно: b >= 2, answer >= 2, a >= 4, a / b = answer.
                val b = random.nextInt(2, 10)
                val answer = random.nextInt(2, 10)
                val a = b * answer

                MathChallenge(
                    question = "$a / $b",
                    answer = answer,
                    difficulty = MathDifficulty.MEDIUM
                )
            }
        }
    }

    // === HARD: +, -, *, деление, скобки (ДОБАВЛЕНО: деление + скобки) ===

    private fun generateHard(random: Random): MathChallenge {
        // ДОБАВЛЕНО: 5 веток вместо 3 (nextInt(5))
        return when (random.nextInt(5)) {
            0 -> {
                val a = random.nextInt(25, 100)
                val b = random.nextInt(25, 100)
                val answer = a + b

                MathChallenge(
                    question = "$a + $b",
                    answer = answer,
                    difficulty = MathDifficulty.HARD
                )
            }

            1 -> {
                val a = random.nextInt(50, 151)
                val b = random.nextInt(15, 50)
                val answer = a - b

                MathChallenge(
                    question = "$a - $b",
                    answer = answer,
                    difficulty = MathDifficulty.HARD
                )
            }

            2 -> {
                val a = random.nextInt(11, 20)
                val b = random.nextInt(6, 15)
                val answer = a * b

                MathChallenge(
                    question = "$a * $b",
                    answer = answer,
                    difficulty = MathDifficulty.HARD
                )
            }

            // ДОБАВЛЕНО: деление с большими числами
            3 -> {
                val b = random.nextInt(3, 13)
                val answer = random.nextInt(3, 16)
                val a = b * answer

                MathChallenge(
                    question = "$a / $b",
                    answer = answer,
                    difficulty = MathDifficulty.HARD
                )
            }

            // ДОБАВЛЕНО: выражения со скобками
            else -> {
                if (random.nextBoolean()) {
                    // (a + b) * c
                    val a = random.nextInt(2, 10)
                    val b = random.nextInt(2, 10)
                    val c = random.nextInt(2, 6)
                    val answer = (a + b) * c

                    MathChallenge(
                        question = "($a + $b) * $c",
                        answer = answer,
                        difficulty = MathDifficulty.HARD
                    )
                } else {
                    // (a - b) * c, гарантированно a > b >= 1
                    val a = random.nextInt(5, 15)
                    val b = random.nextInt(1, a)
                    val c = random.nextInt(2, 6)
                    val answer = (a - b) * c

                    MathChallenge(
                        question = "($a - $b) * $c",
                        answer = answer,
                        difficulty = MathDifficulty.HARD
                    )
                }
            }
        }
    }
}