package com.personal.sleepalarm.domain.assistant

import com.personal.sleepalarm.data.db.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanCommandParserTest {
    @Test
    fun parsesExplicitRussianTaskCommands() {
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyTarget("Алгебра", 120)
            ),
            DailyPlanCommandParser.parse("Установи дневную норму для «Алгебра» 2 часа")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskBoutDuration("Алгебра", 40)
            ),
            DailyPlanCommandParser.parse("Поставь длительность захода для Алгебра 40 минут")
        )
    }

    @Test
    fun parsesGlobalCommandsAndCutoffWithoutNetworkInference() {
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetUrgencyEnabled(false)),
            DailyPlanCommandParser.parse("Отключи срочность дневного плана")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetRepeatEnabled(false)),
            DailyPlanCommandParser.parse("Отключи повторы дневного плана")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetMorningReminderEnabled(false)),
            DailyPlanCommandParser.parse("Выключи утренние напоминания о дневном плане")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetUrgencyBuffer(60)),
            DailyPlanCommandParser.parse("Установи буфер срочности 1 час")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetRepeatInterval(15)),
            DailyPlanCommandParser.parse("Задай интервал повторения дневного плана 15 минут")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetCutoffMinutesOfDay(23 * 60 + 30)),
            DailyPlanCommandParser.parse("Считать день до 23:30")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(DailyPlanCommand.SetDailyPlanSignalVolume(40)),
            DailyPlanCommandParser.parse("Громкость дневного плана 40 процентов")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetDailyPlanSignalMode(DailyPlanSignalMode.SILENT)
            ),
            DailyPlanCommandParser.parse("Звук дневного плана без звука")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetDailyPlanSignalMode(DailyPlanSignalMode.SYSTEM)
            ),
            DailyPlanCommandParser.parse("Set daily plan sound to system")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired("диплом", required = true)
            ),
            DailyPlanCommandParser.parse("Добавь диплом в обязательный план")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired("Диплом", required = false)
            ),
            DailyPlanCommandParser.parse("Диплом необязательный")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired("диплом", required = false)
            ),
            DailyPlanCommandParser.parse("Убери диплом из обязательного плана")
        )
        assertEquals(
            DailyPlanParseResult.Parsed(
                DailyPlanCommand.SetTaskDailyRequired("Thesis", required = true)
            ),
            DailyPlanCommandParser.parse("Include Thesis in the required plan")
        )
        assertTrue(
            DailyPlanCommandParser.parse("Сделай диплом обязательным сегодня") is
                DailyPlanParseResult.Invalid
        )
    }

    @Test
    fun rejectsUnsafeOrOutOfRangeWrites() {
        assertTrue(
            DailyPlanCommandParser.parse("Поставь дневную норму когда-нибудь") is
                DailyPlanParseResult.Invalid
        )
        assertEquals(
            DailyPlanParseResult.Invalid(DailyPlanCommandError.BOUT_DURATION_OUT_OF_RANGE),
            DailyPlanCommandParser.parse("Поставь длительность захода Алгебра 4 минуты")
        )
        assertEquals(
            DailyPlanParseResult.Invalid(DailyPlanCommandError.DAILY_TARGET_OUT_OF_RANGE),
            DailyPlanCommandParser.parse("Поставь дневную норму Алгебра 0 минут")
        )
        assertEquals(
            DailyPlanParseResult.Invalid(DailyPlanCommandError.SIGNAL_VOLUME_OUT_OF_RANGE),
            DailyPlanCommandParser.parse("Громкость дневного плана 101 процент")
        )
        assertEquals(
            DailyPlanParseResult.Invalid(DailyPlanCommandError.INVALID_TIME),
            DailyPlanCommandParser.parse("Контрольное время 24:00")
        )
        assertEquals(
            DailyPlanParseResult.NotCommand,
            DailyPlanCommandParser.parse("Как мне лучше распределить алгебру сегодня?")
        )
    }

    @Test
    fun taskMatcherRequiresOneExactActiveTask() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Алгебра"),
            TaskEntity(id = 2, title = "Алгебра"),
            TaskEntity(id = 3, title = "Алгебра ЕГЭ"),
            TaskEntity(id = 4, title = "Физика", isDone = true),
            TaskEntity(id = 5, title = "Зарядка", isMorningRoutine = true)
        )

        assertTrue(DailyPlanTaskMatcher.match(tasks, "алгебра") is DailyPlanTaskMatch.Ambiguous)
        assertEquals(
            DailyPlanTaskMatch.Unique(tasks[2]),
            DailyPlanTaskMatcher.match(tasks, "  АЛГЕБРА   ЕГЭ ")
        )
        assertEquals(DailyPlanTaskMatch.Missing, DailyPlanTaskMatcher.match(tasks, "алг"))
        assertEquals(DailyPlanTaskMatch.Missing, DailyPlanTaskMatcher.match(tasks, "физика"))
        assertEquals(DailyPlanTaskMatch.Missing, DailyPlanTaskMatcher.match(tasks, "зарядка"))
    }
}
