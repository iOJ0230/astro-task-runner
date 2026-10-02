package com.github.ioj0230.astro.core.calendar

import com.github.ioj0230.astro.infra.calendar.DummyAstroCalendarProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AstroCalendarServiceTest {
    // 2026-10-08T20:00Z is already 2026-10-09 in Manila (UTC+8)
    private val clock = Clock.fixed(Instant.parse("2026-10-08T20:00:00Z"), ZoneOffset.UTC)
    private val service = AstroCalendarService(listOf(DummyAstroCalendarProvider()), clock)

    @Test
    fun `returns only events inside the requested window, sorted by date`() {
        val response = service.upcoming(AstroCalendarRequest(timeZoneId = "Asia/Manila", startDateIso = "2026-10-09", days = 3))

        assertEquals("2026-10-09", response.startDateIso)
        assertEquals("2026-10-11", response.endDateIso)
        assertEquals(
            listOf("2026-10-09", "2026-10-10", "2026-10-10", "2026-10-11"),
            response.events.map { it.dateIso },
        )
        assertTrue(response.events.all { it.source.startsWith("dummy") })
    }

    @Test
    fun `missing start date means today in the requested time zone`() {
        val response = service.upcoming(AstroCalendarRequest(timeZoneId = "Asia/Manila", days = 1))

        assertEquals("2026-10-09", response.startDateIso)
        assertEquals(listOf("Draconid meteor shower"), response.events.map { it.title })
    }

    @Test
    fun `merges providers and drops events a provider returned out of range`() {
        val sloppy =
            object : AstroCalendarProvider {
                override val name = "Sloppy test provider"

                override fun events(
                    start: LocalDate,
                    endInclusive: LocalDate,
                    zoneId: ZoneId,
                ) = listOf(
                    AstroCalendarEvent("2026-10-21", AstroEventCategory.MOON_PHASE, "In range", source = "test"),
                    AstroCalendarEvent("2027-01-01", AstroEventCategory.MOON_PHASE, "Out of range", source = "test"),
                )
            }
        val merged = AstroCalendarService(listOf(DummyAstroCalendarProvider(), sloppy), clock)

        val titles =
            merged.upcoming(AstroCalendarRequest(timeZoneId = "UTC", startDateIso = "2026-10-21", days = 1))
                .events.map { it.title }

        assertEquals(listOf("In range", "Orionid meteor shower"), titles)
    }

    @Test
    fun `rejects out-of-range day counts`() {
        assertFailsWith<IllegalArgumentException> {
            service.upcoming(AstroCalendarRequest(timeZoneId = "UTC", days = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            service.upcoming(AstroCalendarRequest(timeZoneId = "UTC", days = MAX_CALENDAR_DAYS + 1))
        }
    }

    private class BrokenProvider(override val name: String) : AstroCalendarProvider {
        override fun events(
            start: LocalDate,
            endInclusive: LocalDate,
            zoneId: ZoneId,
        ): List<AstroCalendarEvent> = throw java.io.IOException("$name timed out")
    }

    @Test
    fun `one failing source gives a partial digest that names what is missing`() {
        val service = AstroCalendarService(listOf(BrokenProvider("USNO moon phases"), DummyAstroCalendarProvider()), clock)

        val response = service.upcoming(AstroCalendarRequest(timeZoneId = "Asia/Manila", startDateIso = "2026-10-21", days = 1))

        assertEquals(listOf("Orionid meteor shower"), response.events.map { it.title })
        assertEquals(listOf("USNO moon phases"), response.unavailableSources)
        assertTrue(response.summary.contains("Incomplete: USNO moon phases unavailable"), response.summary)
    }

    @Test
    fun `every source failing is an error, not an empty sky`() {
        val service = AstroCalendarService(listOf(BrokenProvider("USNO moon phases"), BrokenProvider("IMO showers")), clock)

        val error =
            assertFailsWith<AllCalendarSourcesFailedException> {
                service.upcoming(AstroCalendarRequest(timeZoneId = "UTC", days = 7))
            }
        assertTrue(error.message!!.contains("USNO moon phases: USNO moon phases timed out"), error.message)
        assertTrue(error.message!!.contains("IMO showers"), error.message)
    }
}
