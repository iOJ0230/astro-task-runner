package com.github.ioj0230.astro.infra.meteor

import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.meteor.MeteorShowerEvent
import java.time.*

class DummyAstroEventProvider : AstroEventService {

    override fun upcomingMeteorShowers(request: MeteorAlertRequest): MeteorAlertResponse {
        val zoneId = ZoneId.of(request.timeZoneId)
        val date = LocalDate.parse(request.dateIso)
        val year = date.year

        val events = listOf(
            buildPerseids(year, zoneId),
            buildGeminids(year, zoneId)
        ).filter { shower ->
            val peak = LocalDate.parse(shower.peakDateIso)
            peak >= date.minusDays(5)
        }

        val summary = if (events.isEmpty()) {
            "No major dummy meteor showers near this date."
        } else {
            "Upcoming dummy meteor showers: " +
                    events.joinToString { it.name }
        }

        return MeteorAlertResponse(events, summary)
    }

    private fun buildPerseids(year: Int, zoneId: ZoneId): MeteorShowerEvent {
        val peakDate = LocalDate.of(year, Month.AUGUST, 12)

        val start = LocalDateTime.of(
            peakDate.minusDays(1),
            LocalTime.of(22, 0)
        ).atZone(zoneId).toOffsetDateTime()
        val end = LocalDateTime.of(
            peakDate.plusDays(1),
            LocalTime.of(4, 0)
        ).atZone(zoneId).toOffsetDateTime()

        return MeteorShowerEvent(
            name = "Perseids",
            peakDateIso = peakDate.toString(),        // "2025-08-12"
            zhr = 100,
            radiantConstellation = "Perseus",
            bestViewStartIso = start.toString(),      // "2025-08-11T22:00:00+08:00"
            bestViewEndIso = end.toString(),
            radiantDirection = "NE to E after midnight",
            notes = "Dummy Perseids data for development."
        )
    }

    private fun buildGeminids(year: Int, zoneId: ZoneId): MeteorShowerEvent {
        val peakDate = LocalDate.of(year, Month.DECEMBER, 14)

        val start = LocalDateTime.of(peakDate.minusDays(1), LocalTime.of(22, 0))
            .atZone(zoneId).toOffsetDateTime()
        val end = LocalDateTime.of(peakDate.plusDays(1), LocalTime.of(4, 0))
            .atZone(zoneId).toOffsetDateTime()

        return MeteorShowerEvent(
            name = "Geminids",
            peakDateIso = peakDate.toString(),
            zhr = 120,
            radiantConstellation = "Gemini",
            bestViewStartIso = start.toString(),
            bestViewEndIso = end.toString(),
            radiantDirection = "High overhead late at night",
            notes = "Dummy Geminids data for development."
        )
    }
}