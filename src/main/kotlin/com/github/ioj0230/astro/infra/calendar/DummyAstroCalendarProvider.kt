package com.github.ioj0230.astro.infra.calendar

import com.github.ioj0230.astro.core.calendar.AstroCalendarEvent
import com.github.ioj0230.astro.core.calendar.AstroCalendarProvider
import com.github.ioj0230.astro.core.calendar.AstroEventCategory
import com.github.ioj0230.astro.core.calendar.AstroEventCategory.CLOSE_APPROACH
import com.github.ioj0230.astro.core.calendar.AstroEventCategory.METEOR_SHOWER
import com.github.ioj0230.astro.core.calendar.AstroEventCategory.MOON_PHASE
import com.github.ioj0230.astro.core.calendar.AstroEventCategory.OPPOSITION
import com.github.ioj0230.astro.core.calendar.AstroEventCategory.WELL_PLACED
import java.time.LocalDate
import java.time.ZoneId

/**
 * Placeholder [AstroCalendarProvider]: a hardcoded copy of **October 2026
 * only**, transcribed from the UP Astronomical Society's "AstroCalendar
 * October 2026" poster. Its dates are Philippine local dates (UTC+8) and
 * are returned as-is whatever `zoneId` is asked for. Not independently
 * verified; any other month returns nothing.
 *
 * It exists to pin down the event *shape* end to end (API → task →
 * notification) before real sources land. See docs/ROADMAP.md for the
 * providers meant to replace it.
 */
class DummyAstroCalendarProvider : AstroCalendarProvider {
    override val name = "Dummy October 2026 calendar"

    override fun events(
        start: LocalDate,
        endInclusive: LocalDate,
        zoneId: ZoneId,
    ): List<AstroCalendarEvent> = OCTOBER_2026.filter { LocalDate.parse(it.dateIso) in start..endInclusive }

    private companion object {
        const val SOURCE = "dummy: UP AstroSoc AstroCalendar Oct 2026 (unverified)"

        fun event(
            dateIso: String,
            category: AstroEventCategory,
            title: String,
            details: String? = null,
        ) = AstroCalendarEvent(dateIso, category, title, details, SOURCE)

        val OCTOBER_2026 =
            listOf(
                event("2026-10-02", WELL_PLACED, "Andromeda Galaxy is well placed"),
                event("2026-10-03", MOON_PHASE, "Last Quarter Moon"),
                event("2026-10-03", WELL_PLACED, "NGC 253 is well placed"),
                event("2026-10-04", OPPOSITION, "Saturn at opposition"),
                event("2026-10-05", CLOSE_APPROACH, "Moon–M44 close approach"),
                event("2026-10-09", METEOR_SHOWER, "Draconid meteor shower"),
                event("2026-10-10", MOON_PHASE, "New Moon", "Darkest skies of the month"),
                event("2026-10-10", METEOR_SHOWER, "Southern Taurid meteor shower"),
                event("2026-10-11", METEOR_SHOWER, "δ-Aurigid meteor shower"),
                event("2026-10-13", OPPOSITION, "Asteroid 4 Vesta at opposition"),
                event("2026-10-15", WELL_PLACED, "Triangulum Galaxy is well placed"),
                event("2026-10-19", MOON_PHASE, "First Quarter Moon"),
                event("2026-10-21", METEOR_SHOWER, "Orionid meteor shower"),
                event("2026-10-24", CLOSE_APPROACH, "Moon–Saturn close approach"),
                event("2026-10-24", METEOR_SHOWER, "Leonis Minorid meteor shower"),
                event("2026-10-26", MOON_PHASE, "Full Moon (Hunter's Moon)", "Bright moon washes out faint targets"),
                event("2026-10-26", WELL_PLACED, "Perseus Double Cluster is well placed"),
            )
    }
}
