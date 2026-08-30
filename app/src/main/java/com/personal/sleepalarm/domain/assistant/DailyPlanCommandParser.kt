package com.personal.sleepalarm.domain.assistant

import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.domain.model.primaryLabel
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * A deliberately small, offline grammar for settings that change the daily plan.
 *
 * The parser is anchored: a sentence either consists entirely of one supported
 * command or it is not a command. This prevents a conversational answer from
 * accidentally being interpreted as a write request.
 */
object DailyPlanCommandParser {
    private val russianDailyTarget = Regex(
        "^(?:установи|поставь|задай|измени)\\s+" +
            "(?:дневную\\s+норму|норму\\s+на\\s+день)\\s+" +
            "(?:для\\s+)?(.+?)\\s+(?:на\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*" +
            "(мин(?:ута|уты|ут)?|ч(?:ас|аса|асов)?)$",
        RegexOption.IGNORE_CASE
    )
    private val englishDailyTarget = Regex(
        "^(?:set|change)\\s+(?:the\\s+)?daily\\s+(?:focus\\s+)?target\\s+" +
            "(?:for\\s+)?(.+?)\\s+(?:to\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*" +
            "(minutes?|mins?|hours?|hrs?)$",
        RegexOption.IGNORE_CASE
    )
    private val russianBoutDuration = Regex(
        "^(?:установи|поставь|задай|измени)\\s+" +
            "(?:длительность\\s+захода|длину\\s+захода|один\\s+заход)\\s+" +
            "(?:для\\s+)?(.+?)\\s+(?:на\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*" +
            "(мин(?:ута|уты|ут)?|ч(?:ас|аса|асов)?)$",
        RegexOption.IGNORE_CASE
    )
    private val englishBoutDuration = Regex(
        "^(?:set|change)\\s+(?:the\\s+)?(?:focus\\s+bout|session)\\s+duration\\s+" +
            "(?:for\\s+)?(.+?)\\s+(?:to\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*" +
            "(minutes?|mins?|hours?|hrs?)$",
        RegexOption.IGNORE_CASE
    )
    private val russianBuffer = Regex(
        "^(?:установи|поставь|задай|измени)\\s+(?:буфер|запас)\\s+" +
            "(?:срочности|дневного\\s+плана)\\s+(?:на\\s+)?" +
            "([0-9]+(?:[.,][0-9]+)?)\\s*(мин(?:ута|уты|ут)?|ч(?:ас|аса|асов)?)$",
        RegexOption.IGNORE_CASE
    )
    private val englishBuffer = Regex(
        "^(?:set|change)\\s+(?:the\\s+)?(?:urgency|daily\\s+plan)\\s+buffer\\s+" +
            "(?:to\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*(minutes?|mins?|hours?|hrs?)$",
        RegexOption.IGNORE_CASE
    )
    private val russianRepeat = Regex(
        "^(?:установи|поставь|задай|измени)\\s+" +
            "(?:повтор|интервал\\s+повторения)\\s+(?:дневного\\s+плана\\s+)?(?:на\\s+)?" +
            "([0-9]+(?:[.,][0-9]+)?)\\s*(мин(?:ута|уты|ут)?|ч(?:ас|аса|асов)?)$",
        RegexOption.IGNORE_CASE
    )
    private val englishRepeat = Regex(
        "^(?:set|change)\\s+(?:the\\s+)?daily\\s+plan\\s+repeat\\s+" +
            "(?:to\\s+)?([0-9]+(?:[.,][0-9]+)?)\\s*(minutes?|mins?|hours?|hrs?)$",
        RegexOption.IGNORE_CASE
    )
    private val russianCutoff = Regex(
        "^(?:считать\\s+день\\s+до|контрольное\\s+время)\\s+([0-2]?[0-9])[:.]([0-5][0-9])$",
        RegexOption.IGNORE_CASE
    )
    private val englishCutoff = Regex(
        "^(?:count\\s+the\\s+day\\s+until|daily\\s+cutoff)\\s+([0-2]?[0-9])[:.]([0-5][0-9])$",
        RegexOption.IGNORE_CASE
    )
    private val russianEnable = Regex(
        "^(включи|отключи)\\s+(?:контроль|напоминания|срочность)\\s+" +
            "(?:дневного\\s+плана|дневной\\s+нормы)$",
        RegexOption.IGNORE_CASE
    )
    private val englishEnable = Regex(
        "^(enable|disable)\\s+(?:daily\\s+plan|daily\\s+target)\\s+" +
            "(?:checks|reminders|urgency)$",
        RegexOption.IGNORE_CASE
    )
    private val russianRepeatEnable = Regex(
        "^(включи|отключи)\\s+повторы\\s+дневного\\s+плана$",
        RegexOption.IGNORE_CASE
    )
    private val englishRepeatEnable = Regex(
        "^(enable|disable)\\s+daily\\s+plan\\s+repeats$",
        RegexOption.IGNORE_CASE
    )
    private val russianMorningReminderEnable = Regex(
        "^(включи|отключи|выключи)\\s+утренние\\s+напоминания\\s+" +
            "(?:о\\s+)?дневн(?:ом|ого)\\s+план(?:е|а)$",
        RegexOption.IGNORE_CASE
    )
    private val englishMorningReminderEnable = Regex(
        "^(enable|disable)\\s+morning\\s+daily\\s+plan\\s+reminders$",
        RegexOption.IGNORE_CASE
    )
    private val russianSignalVolume = Regex(
        "^(?:(?:установи|поставь|задай|измени)\\s+)?громкость\\s+" +
            "(?:сигнала\\s+)?дневного\\s+плана\\s+(?:на\\s+)?([0-9]+)\\s*" +
            "(?:%|процент(?:а|ов)?)$",
        RegexOption.IGNORE_CASE
    )
    private val englishSignalVolume = Regex(
        "^(?:(?:set|change)\\s+)?daily\\s+plan\\s+(?:signal\\s+)?volume\\s+" +
            "(?:to\\s+)?([0-9]+)\\s*(?:%|percent)$",
        RegexOption.IGNORE_CASE
    )
    private val russianSignalMode = Regex(
        "^(?:(?:установи|поставь|задай|измени)\\s+)?звук\\s+дневного\\s+плана\\s+" +
            "(?:на\\s+)?(без\\s+звука|системн(?:ый|ое))$",
        RegexOption.IGNORE_CASE
    )
    private val englishSignalMode = Regex(
        "^(?:(?:set|change)\\s+)?daily\\s+plan\\s+sound\\s+" +
            "(?:to\\s+)?(silent|system|default)$",
        RegexOption.IGNORE_CASE
    )
    private val russianRequired = Regex(
        "^(?:сделай\\s+(.+?)\\s+обязательн(?:ым|ой)|" +
            "добавь\\s+(.+?)\\s+в\\s+обязательный(?:\\s+дневной)?\\s+план)$",
        RegexOption.IGNORE_CASE
    )
    private val russianOptionalCommand = Regex(
        "^(?:сделай\\s+(.+?)\\s+необязательн(?:ым|ой)|" +
            "убери\\s+(.+?)\\s+из\\s+обязательного(?:\\s+дневного)?\\s+плана)$",
        RegexOption.IGNORE_CASE
    )
    private val russianOptional = Regex(
        "^(.+?)\\s+необязательн(?:ый|ая|ое)$",
        RegexOption.IGNORE_CASE
    )
    private val englishRequired = Regex(
        "^(?:make\\s+(.+?)\\s+required|" +
            "include\\s+(.+?)\\s+in\\s+(?:the\\s+)?required(?:\\s+daily)?\\s+plan)$",
        RegexOption.IGNORE_CASE
    )
    private val englishOptional = Regex(
        "^(?:make\\s+(.+?)\\s+optional|" +
            "remove\\s+(.+?)\\s+from\\s+(?:the\\s+)?required(?:\\s+daily)?\\s+plan)$",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): DailyPlanParseResult {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return DailyPlanParseResult.NotCommand

        parseTaskDuration(text, russianDailyTarget, DailyPlanCommandType.DAILY_TARGET)
            ?.let { return it }
        parseTaskDuration(text, englishDailyTarget, DailyPlanCommandType.DAILY_TARGET)
            ?.let { return it }
        parseTaskDuration(text, russianBoutDuration, DailyPlanCommandType.BOUT_DURATION)
            ?.let { return it }
        parseTaskDuration(text, englishBoutDuration, DailyPlanCommandType.BOUT_DURATION)
            ?.let { return it }
        parseGlobalDuration(text, russianBuffer, DailyPlanCommandType.URGENCY_BUFFER)
            ?.let { return it }
        parseGlobalDuration(text, englishBuffer, DailyPlanCommandType.URGENCY_BUFFER)
            ?.let { return it }
        parseGlobalDuration(text, russianRepeat, DailyPlanCommandType.REPEAT_INTERVAL)
            ?.let { return it }
        parseGlobalDuration(text, englishRepeat, DailyPlanCommandType.REPEAT_INTERVAL)
            ?.let { return it }
        parseCutoff(text, russianCutoff)?.let { return it }
        parseCutoff(text, englishCutoff)?.let { return it }
        parseSignalVolume(text, russianSignalVolume)?.let { return it }
        parseSignalVolume(text, englishSignalVolume)?.let { return it }
        parseSignalMode(text, russianSignalMode)?.let { return it }
        parseSignalMode(text, englishSignalMode)?.let { return it }
        russianRequired.matchEntire(text)?.let { match ->
            val task = match.firstNonBlankCapture().trimTaskQuery()
            if (task.isEmpty()) return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired(taskQuery = task, required = true)
            )
        }
        russianOptionalCommand.matchEntire(text)?.let { match ->
            val task = match.firstNonBlankCapture().trimTaskQuery()
            if (task.isEmpty()) return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired(taskQuery = task, required = false)
            )
        }
        russianOptional.matchEntire(text)?.let { match ->
            val task = match.groupValues[1].trimTaskQuery()
            if (task.isEmpty()) return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired(taskQuery = task, required = false)
            )
        }
        englishRequired.matchEntire(text)?.let { match ->
            val task = match.firstNonBlankCapture().trimTaskQuery()
            if (task.isEmpty()) return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired(taskQuery = task, required = true)
            )
        }
        englishOptional.matchEntire(text)?.let { match ->
            val task = match.firstNonBlankCapture().trimTaskQuery()
            if (task.isEmpty()) return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired(taskQuery = task, required = false)
            )
        }

        russianEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetUrgencyEnabled(
                    enabled = match.groupValues[1].equals("включи", ignoreCase = true)
                )
            )
        }
        englishEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetUrgencyEnabled(
                    enabled = match.groupValues[1].equals("enable", ignoreCase = true)
                )
            )
        }
        russianRepeatEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetRepeatEnabled(
                    enabled = match.groupValues[1].equals("включи", ignoreCase = true)
                )
            )
        }
        englishRepeatEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetRepeatEnabled(
                    enabled = match.groupValues[1].equals("enable", ignoreCase = true)
                )
            )
        }
        russianMorningReminderEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetMorningReminderEnabled(
                    enabled = match.groupValues[1].equals("включи", ignoreCase = true)
                )
            )
        }
        englishMorningReminderEnable.matchEntire(text)?.let { match ->
            return DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetMorningReminderEnabled(
                    enabled = match.groupValues[1].equals("enable", ignoreCase = true)
                )
            )
        }

        return if (looksLikeDailyPlanCommand(text)) {
            DailyPlanParseResult.Invalid(DailyPlanCommandError.SYNTAX)
        } else {
            DailyPlanParseResult.NotCommand
        }
    }

    private fun parseTaskDuration(
        text: String,
        regex: Regex,
        type: DailyPlanCommandType
    ): DailyPlanParseResult? {
        val match = regex.matchEntire(text) ?: return null
        val taskQuery = match.groupValues[1].trimTaskQuery()
        if (taskQuery.isEmpty()) {
            return DailyPlanParseResult.Invalid(DailyPlanCommandError.EMPTY_TASK)
        }
        val minutes = durationMinutes(match.groupValues[2], match.groupValues[3])
            ?: return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_DURATION)
        return when (type) {
            DailyPlanCommandType.DAILY_TARGET -> {
                if (minutes !in DAILY_TARGET_RANGE) {
                    DailyPlanParseResult.Invalid(DailyPlanCommandError.DAILY_TARGET_OUT_OF_RANGE)
                } else {
                    DailyPlanParseResult.Parsed(
                        DailyPlanCommand.SetTaskDailyTarget(taskQuery, minutes)
                    )
                }
            }
            DailyPlanCommandType.BOUT_DURATION -> {
                if (minutes !in BOUT_DURATION_RANGE) {
                    DailyPlanParseResult.Invalid(DailyPlanCommandError.BOUT_DURATION_OUT_OF_RANGE)
                } else {
                    DailyPlanParseResult.Parsed(
                        DailyPlanCommand.SetTaskBoutDuration(taskQuery, minutes)
                    )
                }
            }
            else -> error("Unsupported task command type: $type")
        }
    }

    private fun parseGlobalDuration(
        text: String,
        regex: Regex,
        type: DailyPlanCommandType
    ): DailyPlanParseResult? {
        val match = regex.matchEntire(text) ?: return null
        val minutes = durationMinutes(match.groupValues[1], match.groupValues[2])
            ?: return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_DURATION)
        return when (type) {
            DailyPlanCommandType.URGENCY_BUFFER -> {
                if (minutes !in URGENCY_BUFFER_RANGE) {
                    DailyPlanParseResult.Invalid(DailyPlanCommandError.URGENCY_BUFFER_OUT_OF_RANGE)
                } else {
                    DailyPlanParseResult.Parsed(DailyPlanCommand.SetUrgencyBuffer(minutes))
                }
            }
            DailyPlanCommandType.REPEAT_INTERVAL -> {
                if (minutes !in REPEAT_INTERVAL_RANGE) {
                    DailyPlanParseResult.Invalid(DailyPlanCommandError.REPEAT_INTERVAL_OUT_OF_RANGE)
                } else {
                    DailyPlanParseResult.Parsed(DailyPlanCommand.SetRepeatInterval(minutes))
                }
            }
            else -> error("Unsupported global command type: $type")
        }
    }

    private fun parseCutoff(text: String, regex: Regex): DailyPlanParseResult? {
        val match = regex.matchEntire(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull()
            ?: return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_TIME)
        val minute = match.groupValues[2].toIntOrNull()
            ?: return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_TIME)
        if (hour !in 0..23 || minute !in 0..59) {
            return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_TIME)
        }
        return DailyPlanParseResult.Parsed(
            DailyPlanCommand.SetCutoffMinutesOfDay(hour * 60 + minute)
        )
    }

    private fun parseSignalVolume(text: String, regex: Regex): DailyPlanParseResult? {
        val match = regex.matchEntire(text) ?: return null
        val percent = match.groupValues[1].toIntOrNull()
            ?: return DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_DURATION)
        return if (percent in 0..100) {
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetDailyPlanSignalVolume(percent))
        } else {
            DailyPlanParseResult.Invalid(DailyPlanCommandError.SIGNAL_VOLUME_OUT_OF_RANGE)
        }
    }

    private fun parseSignalMode(text: String, regex: Regex): DailyPlanParseResult? {
        val match = regex.matchEntire(text) ?: return null
        val value = match.groupValues[1].lowercase(Locale.ROOT)
        val mode = if (value == "без звука" || value == "silent") {
            DailyPlanSignalMode.SILENT
        } else {
            DailyPlanSignalMode.SYSTEM
        }
        return DailyPlanParseResult.Parsed(DailyPlanCommand.SetDailyPlanSignalMode(mode))
    }

    private fun durationMinutes(number: String, unit: String): Int? {
        val value = runCatching { BigDecimal(number.replace(',', '.')) }.getOrNull()
            ?: return null
        if (value.signum() < 0) return null
        val multiplier = if (unit.lowercase(Locale.ROOT).startsWith("ч") ||
            unit.lowercase(Locale.ROOT).startsWith("h")
        ) {
            BigDecimal.valueOf(60L)
        } else {
            BigDecimal.ONE
        }
        val minutes = value.multiply(multiplier)
        return runCatching { minutes.setScale(0, RoundingMode.UNNECESSARY).intValueExact() }
            .getOrNull()
    }

    private fun looksLikeDailyPlanCommand(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT).replace('ё', 'е')
        val action = listOf(
            "установи", "поставь", "задай", "измени", "включи", "отключи", "выключи", "сделай",
            "set ", "change ", "enable ", "disable ", "считать день до",
            "контрольное время", "daily cutoff", "count the day until",
            "громкость дневного плана", "громкость сигнала дневного плана",
            "звук дневного плана", "daily plan volume", "daily plan signal volume",
            "daily plan sound"
        ).any(normalized::contains)
        val subject = listOf(
            "дневн", "длительность захода", "длину захода", "один заход",
            "буфер", "запас", "интервал повторения", "контрольное время",
            "daily", "focus bout", "session duration", "urgency buffer", "обязательн",
            "громкость", "звук"
        ).any(normalized::contains)
        return action && subject
    }

    private fun String.trimTaskQuery(): String = trim()
        .removeSurrounding("\"")
        .removeSurrounding("«", "»")
        .trim()

    private fun MatchResult.firstNonBlankCapture(): String = groupValues
        .asSequence()
        .drop(1)
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    private enum class DailyPlanCommandType {
        DAILY_TARGET,
        BOUT_DURATION,
        URGENCY_BUFFER,
        REPEAT_INTERVAL
    }

    const val MIN_DAILY_TARGET_MINUTES = 5
    const val MAX_DAILY_TARGET_MINUTES = 480
    const val MIN_BOUT_DURATION_MINUTES = 5
    const val MAX_BOUT_DURATION_MINUTES = 180
    const val MIN_URGENCY_BUFFER_MINUTES = 0
    const val MAX_URGENCY_BUFFER_MINUTES = 720
    const val MIN_REPEAT_INTERVAL_MINUTES = 5
    const val MAX_REPEAT_INTERVAL_MINUTES = 120

    private val DAILY_TARGET_RANGE = MIN_DAILY_TARGET_MINUTES..MAX_DAILY_TARGET_MINUTES
    private val BOUT_DURATION_RANGE = MIN_BOUT_DURATION_MINUTES..MAX_BOUT_DURATION_MINUTES
    private val URGENCY_BUFFER_RANGE = MIN_URGENCY_BUFFER_MINUTES..MAX_URGENCY_BUFFER_MINUTES
    private val REPEAT_INTERVAL_RANGE = MIN_REPEAT_INTERVAL_MINUTES..MAX_REPEAT_INTERVAL_MINUTES
}

sealed interface DailyPlanCommand {
    data class SetTaskDailyTarget(val taskQuery: String, val minutes: Int) : DailyPlanCommand
    data class SetTaskBoutDuration(val taskQuery: String, val minutes: Int) : DailyPlanCommand
    data class SetTaskDailyRequired(val taskQuery: String, val required: Boolean) : DailyPlanCommand
    data class SetUrgencyEnabled(val enabled: Boolean) : DailyPlanCommand
    data class SetUrgencyBuffer(val minutes: Int) : DailyPlanCommand
    data class SetRepeatEnabled(val enabled: Boolean) : DailyPlanCommand
    data class SetRepeatInterval(val minutes: Int) : DailyPlanCommand
    data class SetMorningReminderEnabled(val enabled: Boolean) : DailyPlanCommand
    data class SetCutoffMinutesOfDay(val minutesOfDay: Int) : DailyPlanCommand
    data class SetDailyPlanSignalVolume(val percent: Int) : DailyPlanCommand
    data class SetDailyPlanSignalMode(val mode: DailyPlanSignalMode) : DailyPlanCommand
}

enum class DailyPlanSignalMode { SYSTEM, SILENT }

sealed interface DailyPlanParseResult {
    data object NotCommand : DailyPlanParseResult
    data class Parsed(val command: DailyPlanCommand) : DailyPlanParseResult
    data class Invalid(val error: DailyPlanCommandError) : DailyPlanParseResult
}

enum class DailyPlanCommandError {
    SYNTAX,
    EMPTY_TASK,
    INVALID_DURATION,
    DAILY_TARGET_OUT_OF_RANGE,
    BOUT_DURATION_OUT_OF_RANGE,
    URGENCY_BUFFER_OUT_OF_RANGE,
    REPEAT_INTERVAL_OUT_OF_RANGE,
    SIGNAL_VOLUME_OUT_OF_RANGE,
    INVALID_TIME
}

sealed interface DailyPlanTaskMatch {
    data class Unique(val task: TaskEntity) : DailyPlanTaskMatch
    data class Ambiguous(val tasks: List<TaskEntity>) : DailyPlanTaskMatch
    data object Missing : DailyPlanTaskMatch
}

/** Exact, case-insensitive matching only; substring/fuzzy matching is unsafe for writes. */
object DailyPlanTaskMatcher {
    fun match(tasks: Iterable<TaskEntity>, query: String): DailyPlanTaskMatch {
        val key = normalize(query)
        if (key.isEmpty()) return DailyPlanTaskMatch.Missing
        val matches = tasks.asSequence()
            .filter { !it.isDone && !it.isMorningRoutine }
            .filter { task ->
                normalize(task.primaryLabel()) == key ||
                    task.title.takeIf(String::isNotBlank)?.let(::normalize) == key
            }
            .distinctBy(TaskEntity::id)
            .toList()
        return when (matches.size) {
            0 -> DailyPlanTaskMatch.Missing
            1 -> DailyPlanTaskMatch.Unique(matches.single())
            else -> DailyPlanTaskMatch.Ambiguous(matches)
        }
    }

    internal fun normalize(value: String): String = value
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("«", "»")
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
}
