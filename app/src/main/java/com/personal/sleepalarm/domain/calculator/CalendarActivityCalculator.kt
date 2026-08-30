package com.personal.sleepalarm.domain.calculator

import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object CalendarActivityCalculator {
    fun millisByDate(
        records: List<ActivityRecordEntity>,
        zone: ZoneId
    ): Map<LocalDate, Long> {
        val valid = records.filter { it.effectiveActivityEndMillis() > it.startedAt }
        val dates = linkedSetOf<LocalDate>()
        valid.forEach { record ->
            var date = Instant.ofEpochMilli(record.startedAt).atZone(zone).toLocalDate()
            val lastDate = Instant.ofEpochMilli(record.effectiveActivityEndMillis() - 1L)
                .atZone(zone).toLocalDate()
            while (!date.isAfter(lastDate)) {
                dates += date
                date = date.plusDays(1)
            }
        }
        return dates.associateWith { date ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            ActivityProgressCalculator.uniqueRecordedMillis(valid, start, end)
        }
    }
}
