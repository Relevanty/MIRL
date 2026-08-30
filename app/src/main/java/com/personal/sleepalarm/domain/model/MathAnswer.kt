package com.personal.sleepalarm.domain.model

import java.math.BigDecimal

/**
 * Typed answer contract for wake-up challenges.
 *
 * The legacy [MathChallenge.answer] remains available, while new generators can use one of
 * these specs. Parsing and comparison deliberately live outside the UI so they can be unit
 * tested and reused by previews or future structured editors.
 */
sealed interface MathAnswerSpec {
    data class Integer(val expected: Long) : MathAnswerSpec

    /** A finite set of integers. Ordering and duplicate input values are ignored. */
    data class IntegerSet(val expected: Set<Long>) : MathAnswerSpec

    /** A union of intervals on the real number line. */
    data class IntervalSet(val expected: List<MathInterval>) : MathAnswerSpec {
        init {
            require(expected.isNotEmpty()) { "Interval answer must contain at least one interval" }
            expected.forEach(::requireValidInterval)
        }
    }

    /** An ordered pair; unlike a set, swapping coordinates changes the answer. */
    data class OrderedPair(
        val first: BigDecimal,
        val second: BigDecimal
    ) : MathAnswerSpec {
        constructor(first: Int, second: Int) : this(first.toBigDecimal(), second.toBigDecimal())
        constructor(first: Long, second: Long) : this(first.toBigDecimal(), second.toBigDecimal())
    }
}

sealed interface MathBoundary {
    data object NegativeInfinity : MathBoundary
    data class Finite(val value: BigDecimal) : MathBoundary {
        constructor(value: Int) : this(value.toBigDecimal())
        constructor(value: Long) : this(value.toBigDecimal())
    }
    data object PositiveInfinity : MathBoundary
}

data class MathInterval(
    val lower: MathBoundary,
    val upper: MathBoundary,
    val lowerInclusive: Boolean = false,
    val upperInclusive: Boolean = false
) {
    companion object {
        fun open(lower: Int, upper: Int) = MathInterval(
            lower = MathBoundary.Finite(lower),
            upper = MathBoundary.Finite(upper)
        )

        fun closed(lower: Int, upper: Int) = MathInterval(
            lower = MathBoundary.Finite(lower),
            upper = MathBoundary.Finite(upper),
            lowerInclusive = true,
            upperInclusive = true
        )

        fun atMost(upper: Int, inclusive: Boolean = true) = MathInterval(
            lower = MathBoundary.NegativeInfinity,
            upper = MathBoundary.Finite(upper),
            upperInclusive = inclusive
        )

        fun atLeast(lower: Int, inclusive: Boolean = true) = MathInterval(
            lower = MathBoundary.Finite(lower),
            upper = MathBoundary.PositiveInfinity,
            lowerInclusive = inclusive
        )
    }
}

sealed interface ParsedMathAnswer {
    data class Integer(val value: Long) : ParsedMathAnswer
    data class IntegerSet(val values: Set<Long>) : ParsedMathAnswer
    data class IntervalSet(val intervals: List<MathInterval>) : ParsedMathAnswer
    data class OrderedPair(val first: BigDecimal, val second: BigDecimal) : ParsedMathAnswer
}

sealed interface MathAnswerParseResult {
    data class Success(
        val value: ParsedMathAnswer,
        val canonical: String
    ) : MathAnswerParseResult

    data class Invalid(val reason: MathAnswerParseError) : MathAnswerParseResult
}

enum class MathAnswerParseError {
    EMPTY,
    INVALID_INTEGER,
    INVALID_SET,
    INVALID_INTERVAL,
    INVALID_PAIR
}

sealed interface MathAnswerValidation {
    data class Correct(val canonicalInput: String) : MathAnswerValidation
    data class Incorrect(val canonicalInput: String) : MathAnswerValidation
    data class Invalid(val reason: MathAnswerParseError) : MathAnswerValidation
}

/** Pure parser, normalizer and validator for all supported wake-up answer shapes. */
object MathAnswerParser {

    private val variablePrefix = Regex(
        pattern = "^\\s*[xXхХ]\\s*(?:∈|=|(?:in)\\b)\\s*",
        option = RegexOption.IGNORE_CASE
    )
    private val unionSeparator = Regex("\\s*(?:∪|[uU])\\s*")

    fun parse(spec: MathAnswerSpec, rawInput: String): MathAnswerParseResult {
        val input = normalizeCommon(rawInput)
        if (input.isBlank()) return MathAnswerParseResult.Invalid(MathAnswerParseError.EMPTY)

        return when (spec) {
            is MathAnswerSpec.Integer -> parseInteger(input)
            is MathAnswerSpec.IntegerSet -> parseIntegerSet(input)
            is MathAnswerSpec.IntervalSet -> parseIntervalSet(input)
            is MathAnswerSpec.OrderedPair -> parseOrderedPair(input)
        }
    }

    fun validate(spec: MathAnswerSpec, rawInput: String): MathAnswerValidation {
        return when (val parsed = parse(spec, rawInput)) {
            is MathAnswerParseResult.Invalid -> MathAnswerValidation.Invalid(parsed.reason)
            is MathAnswerParseResult.Success -> {
                if (matches(spec, parsed.value)) {
                    MathAnswerValidation.Correct(parsed.canonical)
                } else {
                    MathAnswerValidation.Incorrect(parsed.canonical)
                }
            }
        }
    }

    fun canonical(spec: MathAnswerSpec): String = when (spec) {
        is MathAnswerSpec.Integer -> spec.expected.toString()
        is MathAnswerSpec.IntegerSet -> canonicalIntegerSet(spec.expected)
        is MathAnswerSpec.IntervalSet -> canonicalIntervalSet(normalizeIntervals(spec.expected))
        is MathAnswerSpec.OrderedPair -> "(${formatDecimal(spec.first)}; ${formatDecimal(spec.second)})"
    }

    /**
     * Keeps useful mathematical input while preventing an accidentally pasted essay from
     * turning the alarm field into an unbounded state value. Partial expressions are preserved.
     */
    fun sanitizeInput(rawInput: String, maxLength: Int = 160): String = buildString {
        rawInput.forEach { character ->
            val accepted = character.isDigit() || character.isWhitespace() || character in
                "+-−.,;{}[]()∞∅∪∈=xXхХuUinfINFtyTY"
            if (accepted && length < maxLength) {
                append(if (character == '\n' || character == '\r' || character == '\t') ' ' else character)
            }
        }
    }

    fun normalizeIntervals(intervals: List<MathInterval>): List<MathInterval> {
        if (intervals.isEmpty()) return emptyList()
        intervals.forEach(::requireValidInterval)

        val sorted = intervals.sortedWith { left, right ->
            val lowerComparison = compareBoundaries(left.lower, right.lower)
            if (lowerComparison != 0) lowerComparison
            else when {
                left.lowerInclusive == right.lowerInclusive -> 0
                left.lowerInclusive -> -1
                else -> 1
            }
        }

        val result = mutableListOf<MathInterval>()
        sorted.forEach { candidate ->
            val current = result.lastOrNull()
            if (current == null || !canMerge(current, candidate)) {
                result += candidate.withOpenInfiniteEnds()
            } else {
                result[result.lastIndex] = merge(current, candidate)
            }
        }
        return result
    }

    private fun parseInteger(input: String): MathAnswerParseResult {
        val value = input.trim().toLongOrNull()
            ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_INTEGER)
        return MathAnswerParseResult.Success(
            value = ParsedMathAnswer.Integer(value),
            canonical = value.toString()
        )
    }

    private fun parseIntegerSet(input: String): MathAnswerParseResult {
        val trimmed = input.trim()
        if (trimmed == "∅" || trimmed == "{}") {
            return MathAnswerParseResult.Success(ParsedMathAnswer.IntegerSet(emptySet()), "∅")
        }
        val body = when {
            trimmed.startsWith('{') && trimmed.endsWith('}') -> trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith('{') || trimmed.endsWith('}') ->
                return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_SET)
            else -> trimmed
        }
        if (body.isBlank()) {
            return MathAnswerParseResult.Success(ParsedMathAnswer.IntegerSet(emptySet()), "∅")
        }
        val pieces = body.split(';', ',')
        if (pieces.any { it.isBlank() }) {
            return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_SET)
        }
        val values = pieces.map { it.trim().toLongOrNull() ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_SET) }
            .toSet()
        return MathAnswerParseResult.Success(
            value = ParsedMathAnswer.IntegerSet(values),
            canonical = canonicalIntegerSet(values)
        )
    }

    private fun parseIntervalSet(input: String): MathAnswerParseResult {
        val body = input.replaceFirst(variablePrefix, "").trim()
        val parts = body.split(unionSeparator)
        if (parts.isEmpty() || parts.any { it.isBlank() }) {
            return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_INTERVAL)
        }
        val parsed = parts.map { parseInterval(it) ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_INTERVAL) }
        val normalized = runCatching { normalizeIntervals(parsed) }.getOrNull()
            ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_INTERVAL)
        return MathAnswerParseResult.Success(
            value = ParsedMathAnswer.IntervalSet(normalized),
            canonical = canonicalIntervalSet(normalized)
        )
    }

    private fun parseInterval(raw: String): MathInterval? {
        val trimmed = raw.trim()
        if (trimmed.length < 5 || trimmed.first() !in "([" || trimmed.last() !in ")]" ) return null
        val body = trimmed.substring(1, trimmed.lastIndex)
        val separatorIndex = body.indexOf(';')
        if (separatorIndex < 0 || body.indexOf(';', separatorIndex + 1) >= 0) return null
        val lower = parseBoundary(body.substring(0, separatorIndex)) ?: return null
        val upper = parseBoundary(body.substring(separatorIndex + 1)) ?: return null
        val interval = MathInterval(
            lower = lower,
            upper = upper,
            lowerInclusive = trimmed.first() == '[',
            upperInclusive = trimmed.last() == ']'
        )
        return runCatching {
            requireValidInterval(interval)
            interval.withOpenInfiniteEnds()
        }.getOrNull()
    }

    private fun parseOrderedPair(input: String): MathAnswerParseResult {
        val trimmed = input.trim()
        val body = when {
            trimmed.startsWith('(') && trimmed.endsWith(')') -> trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith('(') || trimmed.endsWith(')') ->
                return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_PAIR)
            else -> trimmed
        }
        val pieces = if (';' in body) body.split(';') else body.split(',')
        if (pieces.size != 2) return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_PAIR)
        val first = parseDecimal(pieces[0]) ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_PAIR)
        val second = parseDecimal(pieces[1]) ?: return MathAnswerParseResult.Invalid(MathAnswerParseError.INVALID_PAIR)
        return MathAnswerParseResult.Success(
            value = ParsedMathAnswer.OrderedPair(first, second),
            canonical = "(${formatDecimal(first)}; ${formatDecimal(second)})"
        )
    }

    private fun matches(spec: MathAnswerSpec, value: ParsedMathAnswer): Boolean = when {
        spec is MathAnswerSpec.Integer && value is ParsedMathAnswer.Integer -> spec.expected == value.value
        spec is MathAnswerSpec.IntegerSet && value is ParsedMathAnswer.IntegerSet -> spec.expected == value.values
        spec is MathAnswerSpec.IntervalSet && value is ParsedMathAnswer.IntervalSet ->
            intervalListsEquivalent(normalizeIntervals(spec.expected), value.intervals)
        spec is MathAnswerSpec.OrderedPair && value is ParsedMathAnswer.OrderedPair ->
            decimalEquals(spec.first, value.first) && decimalEquals(spec.second, value.second)
        else -> false
    }

    private fun canonicalIntegerSet(values: Set<Long>): String = if (values.isEmpty()) {
        "∅"
    } else {
        values.sorted().joinToString(prefix = "{", postfix = "}", separator = "; ")
    }

    private fun canonicalIntervalSet(intervals: List<MathInterval>): String =
        "x ∈ " + intervals.joinToString(separator = " ∪ ") { interval ->
            val lowerBracket = if (interval.lowerInclusive) "[" else "("
            val upperBracket = if (interval.upperInclusive) "]" else ")"
            "$lowerBracket${formatBoundary(interval.lower)}; ${formatBoundary(interval.upper)}$upperBracket"
        }

    private fun formatBoundary(boundary: MathBoundary): String = when (boundary) {
        MathBoundary.NegativeInfinity -> "-∞"
        is MathBoundary.Finite -> formatDecimal(boundary.value)
        MathBoundary.PositiveInfinity -> "+∞"
    }

    private fun parseBoundary(raw: String): MathBoundary? {
        val normalized = raw.trim().lowercase().replace('−', '-')
        return when (normalized) {
            "-∞", "-inf", "-infinity" -> MathBoundary.NegativeInfinity
            "+∞", "∞", "+inf", "inf", "+infinity", "infinity" -> MathBoundary.PositiveInfinity
            else -> parseDecimal(normalized)?.let(MathBoundary::Finite)
        }
    }

    private fun parseDecimal(raw: String): BigDecimal? {
        val normalized = raw.trim().replace('−', '-').replace(',', '.')
        return normalized.toBigDecimalOrNull()?.normalized()
    }

    private fun normalizeCommon(input: String): String = input
        .trim()
        .replace('−', '-')

    private fun formatDecimal(value: BigDecimal): String = value.normalized().toPlainString()

    private fun BigDecimal.normalized(): BigDecimal = if (compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal.ZERO
    } else {
        stripTrailingZeros()
    }

    private fun decimalEquals(left: BigDecimal, right: BigDecimal): Boolean = left.compareTo(right) == 0

    private fun intervalListsEquivalent(left: List<MathInterval>, right: List<MathInterval>): Boolean =
        left.size == right.size && left.zip(right).all { (first, second) ->
            first.lowerInclusive == second.lowerInclusive &&
                first.upperInclusive == second.upperInclusive &&
                boundariesEqual(first.lower, second.lower) &&
                boundariesEqual(first.upper, second.upper)
        }

    private fun boundariesEqual(left: MathBoundary, right: MathBoundary): Boolean = when {
        left is MathBoundary.Finite && right is MathBoundary.Finite -> decimalEquals(left.value, right.value)
        else -> left == right
    }

    private fun compareBoundaries(left: MathBoundary, right: MathBoundary): Int = when {
        left === MathBoundary.NegativeInfinity && right === MathBoundary.NegativeInfinity -> 0
        left === MathBoundary.NegativeInfinity -> -1
        right === MathBoundary.NegativeInfinity -> 1
        left === MathBoundary.PositiveInfinity && right === MathBoundary.PositiveInfinity -> 0
        left === MathBoundary.PositiveInfinity -> 1
        right === MathBoundary.PositiveInfinity -> -1
        left is MathBoundary.Finite && right is MathBoundary.Finite -> left.value.compareTo(right.value)
        else -> error("Unknown boundary type")
    }

    private fun canMerge(current: MathInterval, next: MathInterval): Boolean {
        val comparison = compareBoundaries(next.lower, current.upper)
        return comparison < 0 || comparison == 0 && (current.upperInclusive || next.lowerInclusive)
    }

    private fun merge(current: MathInterval, next: MathInterval): MathInterval {
        val upperComparison = compareBoundaries(current.upper, next.upper)
        val upper = if (upperComparison >= 0) current.upper else next.upper
        val upperInclusive = when {
            upperComparison > 0 -> current.upperInclusive
            upperComparison < 0 -> next.upperInclusive
            else -> current.upperInclusive || next.upperInclusive
        }
        return current.copy(
            upper = upper,
            upperInclusive = upperInclusive && upper !== MathBoundary.PositiveInfinity
        )
    }

    private fun MathInterval.withOpenInfiniteEnds(): MathInterval = copy(
        lowerInclusive = lowerInclusive && lower !== MathBoundary.NegativeInfinity,
        upperInclusive = upperInclusive && upper !== MathBoundary.PositiveInfinity
    )
}

private fun requireValidInterval(interval: MathInterval) {
    require(interval.lower !== MathBoundary.PositiveInfinity) { "Lower bound cannot be +infinity" }
    require(interval.upper !== MathBoundary.NegativeInfinity) { "Upper bound cannot be -infinity" }
    require(!interval.lowerInclusive || interval.lower is MathBoundary.Finite) { "Infinite lower bound must be open" }
    require(!interval.upperInclusive || interval.upper is MathBoundary.Finite) { "Infinite upper bound must be open" }

    val order = when {
        interval.lower === MathBoundary.NegativeInfinity -> -1
        interval.upper === MathBoundary.PositiveInfinity -> -1
        interval.lower is MathBoundary.Finite && interval.upper is MathBoundary.Finite ->
            interval.lower.value.compareTo(interval.upper.value)
        else -> 1
    }
    require(order < 0 || order == 0 && interval.lowerInclusive && interval.upperInclusive) {
        "Interval must be non-empty and ordered"
    }
}
