package com.personal.sleepalarm.util

import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экспорт сессий сна в CSV (F5).
 *
 * Пишет в переданный OutputStream (полученный из SAF через
 * contentResolver.openOutputStream). Разрешения на память НЕ нужны.
 *
 * Формат:
 * - разделитель: запятая ',' (универсально для парсеров);
 * - экранирование по RFC 4180: поля с ',', '"', '\n', '\r'
 *   оборачиваются в двойные кавычки, внутренние '"' удваиваются;
 * - кодировка UTF-8 БЕЗ BOM (современные Excel/LibreOffice/Numbers
 *   читают UTF-8 без BOM; BOM ломает некоторые парсеры);
 * - первая строка — заголовок;
 * - время — в ISO-локальном виде в зоне сессии.
 */
object CsvExporter {

    private const val DELIMITER = ','
    private const val QUOTE = '"'
    private const val NEWLINE = "\n"

    private val ISO_LOCAL: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Заголовки колонок. Порядок фиксирован — на него опираются парсеры.
     */
    private val HEADER: List<String> = listOf(
        "id",
        "bed_time",
        "sleep_onset_latency_min",
        "estimated_sleep_start",
        "cycle_length_min",
        "cycles_planned",
        "estimated_wake",
        "actual_wake",
        "dismiss_type",
        "cues_enabled",
        "cues_scheduled",
        "cues_played",
        "cues_skipped",
        "detected_onset_latency_min",
        "duration_min",
        "zone_id",
        "created_at"
    )

    /**
     * Записывает сессии в CSV.
     *
     * @param context не используется напрямую (оставлен для совместимости
     *                сигнатуры и возможного будущего форматирования через ресурсы).
     * @param sessions список сессий для выгрузки.
     * @param outputStream поток из SAF (contentResolver.openOutputStream).
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun writeSessionsCsv(
        context: android.content.Context,
        sessions: List<SleepSessionEntity>,
        outputStream: OutputStream
    ) {
        // BufferedWriter для эффективной записи; UTF-8 без BOM.
        outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writeHeader(writer)

            sessions.forEach { session ->
                writeRow(writer, session)
            }

            writer.flush()
        }
    }

    private fun writeHeader(writer: BufferedWriter) {
        writer.write(HEADER.joinToString(DELIMITER.toString()))
        writer.write(NEWLINE)
    }

    private fun writeRow(
        writer: BufferedWriter,
        session: SleepSessionEntity
    ) {
        val zone = runCatching { ZoneId.of(session.zoneId) }
            .getOrDefault(ZoneId.systemDefault())

        // Длительность «чистого» сна: если есть автоопределённое засыпание (F9) —
        // берём его, иначе расчётное. Конец — фактическое пробуждение или расчётное.
        val start = session.detectedSleepOnsetTime
            ?: session.estimatedSleepStartTime
        val end = session.actualWakeTime ?: session.estimatedWakeTime
        val durationMinutes = ((end - start) / 60_000L).coerceAtLeast(0)

        val fields: List<String> = listOf(
            session.id.toString(),
            formatEpoch(session.bedTimePlanned, zone),
            session.sleepOnsetLatencyMinutes.toString(),
            formatEpoch(session.estimatedSleepStartTime, zone),
            session.cycleLengthMinutes.toString(),
            session.cyclesPlanned.toString(),
            formatEpoch(session.estimatedWakeTime, zone),
            formatEpochNullable(session.actualWakeTime, zone),
            session.dismissType?.name.orEmpty(),
            session.cuesEnabled.toString(),
            session.cuesScheduledCount.toString(),
            session.cuesPlayedCount.toString(),
            session.cuesSkippedCount.toString(),
            formatIntNullable(session.detectedOnsetLatencyMinutes),
            durationMinutes.toString(),
            session.zoneId,
            formatEpoch(session.createdAt, zone)
        )

        writer.write(fields.joinToString(DELIMITER.toString()) { escape(it) })
        writer.write(NEWLINE)
    }

    /**
     * Экранирование поля по RFC 4180.
     */
    private fun escape(value: String): String {
        val needsQuoting = value.indexOf(DELIMITER) >= 0 ||
                value.indexOf(QUOTE) >= 0 ||
                value.indexOf('\n') >= 0 ||
                value.indexOf('\r') >= 0

        return if (needsQuoting) {
            val escaped = value.replace(QUOTE.toString(), "\"\"")
            "$QUOTE$escaped$QUOTE"
        } else {
            value
        }
    }

    private fun formatEpoch(
        epochMillis: Long,
        zone: ZoneId
    ): String {
        return ISO_LOCAL.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }

    private fun formatEpochNullable(
        epochMillis: Long?,
        zone: ZoneId
    ): String {
        return epochMillis?.let { formatEpoch(it, zone) }.orEmpty()
    }

    private fun formatIntNullable(value: Int?): String {
        return value?.toString().orEmpty()
    }
}