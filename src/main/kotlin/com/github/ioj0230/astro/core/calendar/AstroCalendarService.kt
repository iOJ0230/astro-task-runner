package com.github.ioj0230.astro.core.calendar

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Upper bound on [AstroCalendarRequest.days]; roughly one quarter. */
const val MAX_CALENDAR_DAYS = 92

class AstroCalendarService(
    private val providers: List<AstroCalendarProvider>,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun upcoming(request: AstroCalendarRequest): AstroCalendarResponse {
        require(request.days in 1..MAX_CALENDAR_DAYS) { "days must be within 1..$MAX_CALENDAR_DAYS" }
        val zoneId = ZoneId.of(request.timeZoneId)
        val start = request.startDateIso?.let(LocalDate::parse) ?: LocalDate.now(clock.withZone(zoneId))
        val end = start.plusDays(request.days - 1L)

        val events =
            providers
                .flatMap { it.events(start, end, zoneId) }
                // Don't trust every provider to filter correctly.
                .filter { LocalDate.parse(it.dateIso) in start..end }
                .distinct()
                .sortedWith(compareBy({ it.dateIso }, { it.category }, { it.title }))

        val summary =
            if (events.isEmpty()) {
                "No sky events found between $start and $end."
            } else {
                "${events.size} sky event(s) between $start and $end."
            }

        return AstroCalendarResponse(
            startDateIso = start.toString(),
            endDateIso = end.toString(),
            timeZoneId = request.timeZoneId,
            events = events,
            summary = summary,
        )
    }
}
