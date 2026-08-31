package com.personal.sleepalarm.domain.adaptive

/** Pure interval subtraction and placement utilities for immutable commitments. */
object AvailabilityCalculator {
    private const val MILLIS_PER_MINUTE = 60_000L

    fun calculate(
        horizonStartMillis: Long,
        horizonEndMillis: Long,
        fixedWindows: Iterable<TimeWindow>,
        minimumFreeMinutes: Int = 1
    ): AvailabilityResult {
        if (horizonEndMillis <= horizonStartMillis || minimumFreeMinutes < 0) {
            return AvailabilityResult(emptyList(), emptyList())
        }

        val clamped = fixedWindows.mapNotNull { window ->
            val start = maxOf(horizonStartMillis, window.startMillis)
            val end = minOf(horizonEndMillis, window.endMillis)
            if (end > start) TimeWindow(start, end) else null
        }.sortedWith(compareBy<TimeWindow> { it.startMillis }.thenBy { it.endMillis })

        val merged = mutableListOf<TimeWindow>()
        clamped.forEach { window ->
            val previous = merged.lastOrNull()
            if (previous == null || window.startMillis > previous.endMillis) {
                merged += window
            } else if (window.endMillis > previous.endMillis) {
                merged[merged.lastIndex] = TimeWindow(previous.startMillis, window.endMillis)
            }
        }

        val minimumFreeMillis = minimumFreeMinutes.toLong() * MILLIS_PER_MINUTE
        val free = mutableListOf<TimeWindow>()
        var cursor = horizonStartMillis
        merged.forEach { busy ->
            if (busy.startMillis > cursor && busy.startMillis - cursor >= minimumFreeMillis) {
                free += TimeWindow(cursor, busy.startMillis)
            }
            cursor = maxOf(cursor, busy.endMillis)
        }
        if (horizonEndMillis > cursor && horizonEndMillis - cursor >= minimumFreeMillis) {
            free += TimeWindow(cursor, horizonEndMillis)
        }

        return AvailabilityResult(freeWindows = free, mergedFixedWindows = merged)
    }

    /**
     * Finds the first interval that respects availability, release/fixed time,
     * and an optional strict finish boundary.
     */
    fun findEarliestPlacement(
        freeWindows: Iterable<TimeWindow>,
        durationMinutes: Int,
        notBeforeMillis: Long,
        earliestStartMillis: Long? = null,
        fixedStartMillis: Long? = null,
        finishByMillis: Long? = null
    ): TimeWindow? {
        if (durationMinutes <= 0) return null
        val durationMillis = durationMinutes.toLong() * MILLIS_PER_MINUTE
        val orderedWindows = freeWindows.sortedBy(TimeWindow::startMillis)

        if (fixedStartMillis != null) {
            if (fixedStartMillis < notBeforeMillis ||
                (earliestStartMillis != null && fixedStartMillis < earliestStartMillis)
            ) {
                return null
            }
            val end = safeAdd(fixedStartMillis, durationMillis) ?: return null
            if (finishByMillis != null && end > finishByMillis) return null
            val containing = orderedWindows.firstOrNull { window ->
                fixedStartMillis >= window.startMillis && end <= window.endMillis
            } ?: return null
            return TimeWindow(maxOf(fixedStartMillis, containing.startMillis), end)
        }

        val earliest = maxOf(notBeforeMillis, earliestStartMillis ?: Long.MIN_VALUE)
        orderedWindows.forEach { window ->
            val start = maxOf(window.startMillis, earliest)
            val end = safeAdd(start, durationMillis) ?: return@forEach
            if (end <= window.endMillis && (finishByMillis == null || end <= finishByMillis)) {
                return TimeWindow(start, end)
            }
        }
        return null
    }

    private fun safeAdd(value: Long, increment: Long): Long? =
        runCatching { Math.addExact(value, increment) }.getOrNull()
}
