package com.github.ioj0230.astro.core.calendar

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

/**
 * Event kinds, modelled on the monthly "AstroCalendar" posters astronomy
 * societies publish (moon phases down one side, dated events down the other).
 */
@Serializable
enum class AstroEventCategory {
    MOON_PHASE,
    METEOR_SHOWER,

    /** A planet or asteroid at opposition: closest and brightest, up all night. */
    OPPOSITION,

    /** Two objects appearing close together (e.g. Moon–Saturn). */
    CLOSE_APPROACH,

    /** A deep-sky object well placed for evening viewing. */
    WELL_PLACED,
}

@Serializable
data class AstroCalendarEvent(
    // Local date in the request's time zone, e.g. "2026-10-21"
    val dateIso: String,
    val category: AstroEventCategory,
    // e.g. "Orionid Meteor Shower"
    val title: String,
    val details: String? = null,
    // Where this came from, e.g. "USNO" or "dummy: ...". Always set, so
    // placeholder data can never pass for verified data.
    val source: String,
)

@Serializable
data class AstroCalendarRequest(
    val timeZoneId: String,
    // null = "today" in timeZoneId, resolved at run time. Leave it null for
    // scheduled tasks so a DAILY task doesn't report the same week forever.
    val startDateIso: String? = null,
    val days: Int = 7,
)

@Serializable
data class AstroCalendarResponse(
    val startDateIso: String,
    val endDateIso: String,
    val timeZoneId: String,
    val events: List<AstroCalendarEvent>,
    val summary: String,
)

/**
 * One source of calendar events (a static dataset, an external API, an
 * ephemeris calculation, ...). [AstroCalendarService] merges all of them,
 * so adding a source never touches the others.
 */
interface AstroCalendarProvider {
    /** Events whose local date in [zoneId] falls within [start]..[endInclusive]. */
    fun events(
        start: LocalDate,
        endInclusive: LocalDate,
        zoneId: ZoneId,
    ): List<AstroCalendarEvent>
}
