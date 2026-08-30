package com.personal.sleepalarm.util

import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathDifficulty
import kotlin.random.Random

/** Entry point for every wake-up math difficulty. */
object MathChallengeGenerator {

    fun generate(
        difficulty: MathDifficulty,
        random: Random = Random.Default
    ): MathChallenge = when (difficulty) {
        MathDifficulty.EASY -> generateEasy(random)
        MathDifficulty.MEDIUM -> generateMedium(random)
        MathDifficulty.HARD -> generateHard(random)
        MathDifficulty.EXPERT,
        MathDifficulty.EXTREME -> EquationChallengeGenerator.generate(difficulty, random)
    }

    /** EASY intentionally keeps the original small addition/subtraction contract. */
    private fun generateEasy(random: Random): MathChallenge = when (random.nextInt(2)) {
        0 -> {
            val first = random.nextInt(5, 21)
            val second = random.nextInt(5, 21)
            MathChallenge(
                question = "$first + $second",
                answer = first + second,
                difficulty = MathDifficulty.EASY
            )
        }

        else -> {
            val first = random.nextInt(10, 31)
            val second = random.nextInt(5, first)
            MathChallenge(
                question = "$first - $second",
                answer = first - second,
                difficulty = MathDifficulty.EASY
            )
        }
    }

    /** MEDIUM keeps all four arithmetic operations and exact integer division. */
    private fun generateMedium(random: Random): MathChallenge = when (random.nextInt(4)) {
        0 -> {
            val first = random.nextInt(12, 50)
            val second = random.nextInt(10, 50)
            MathChallenge(
                question = "$first + $second",
                answer = first + second,
                difficulty = MathDifficulty.MEDIUM
            )
        }

        1 -> {
            val first = random.nextInt(20, 81)
            val second = random.nextInt(5, first)
            MathChallenge(
                question = "$first - $second",
                answer = first - second,
                difficulty = MathDifficulty.MEDIUM
            )
        }

        2 -> {
            val first = random.nextInt(3, 10)
            val second = random.nextInt(4, 10)
            MathChallenge(
                question = "$first * $second",
                answer = first * second,
                difficulty = MathDifficulty.MEDIUM
            )
        }

        else -> {
            val divisor = random.nextInt(2, 10)
            val answer = random.nextInt(2, 10)
            MathChallenge(
                question = "${divisor * answer} / $divisor",
                answer = answer,
                difficulty = MathDifficulty.MEDIUM
            )
        }
    }

    /** HARD keeps larger arithmetic, exact division and parenthesised expressions. */
    private fun generateHard(random: Random): MathChallenge = when (random.nextInt(5)) {
        0 -> {
            val first = random.nextInt(25, 100)
            val second = random.nextInt(25, 100)
            MathChallenge(
                question = "$first + $second",
                answer = first + second,
                difficulty = MathDifficulty.HARD
            )
        }

        1 -> {
            val first = random.nextInt(50, 151)
            val second = random.nextInt(15, 50)
            MathChallenge(
                question = "$first - $second",
                answer = first - second,
                difficulty = MathDifficulty.HARD
            )
        }

        2 -> {
            val first = random.nextInt(11, 20)
            val second = random.nextInt(6, 15)
            MathChallenge(
                question = "$first * $second",
                answer = first * second,
                difficulty = MathDifficulty.HARD
            )
        }

        3 -> {
            val divisor = random.nextInt(3, 13)
            val answer = random.nextInt(3, 16)
            MathChallenge(
                question = "${divisor * answer} / $divisor",
                answer = answer,
                difficulty = MathDifficulty.HARD
            )
        }

        else -> {
            if (random.nextBoolean()) {
                val first = random.nextInt(2, 10)
                val second = random.nextInt(2, 10)
                val multiplier = random.nextInt(2, 6)
                MathChallenge(
                    question = "($first + $second) * $multiplier",
                    answer = (first + second) * multiplier,
                    difficulty = MathDifficulty.HARD
                )
            } else {
                val first = random.nextInt(5, 15)
                val second = random.nextInt(1, first)
                val multiplier = random.nextInt(2, 6)
                MathChallenge(
                    question = "($first - $second) * $multiplier",
                    answer = (first - second) * multiplier,
                    difficulty = MathDifficulty.HARD
                )
            }
        }
    }
}
