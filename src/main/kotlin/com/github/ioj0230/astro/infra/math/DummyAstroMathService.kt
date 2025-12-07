package com.github.ioj0230.astro.infra.math

import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.darkwindow.DarkWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

class DummyAstroMathService : AstroMathService {

    /**
     * 🌙 SUPER DUMB BUT PREDICTABLE LOGIC:
     *     - Assume "dark" is roughly 20:00–03:00 local time
     *     - Later replace this with real sun/moon calculations
     */
    override fun computeDarkWindow(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        timeZoneId: String
    ): DarkWindow {
        val zoneId = ZoneId.of(timeZoneId)

        val startLocal = LocalDateTime.of(date, LocalTime.of(20, 0))
        val endLocal = LocalDateTime.of(date.plusDays(1), LocalTime.of(3, 0))

        val start = startLocal.atZone(zoneId).toOffsetDateTime()
        val end = endLocal.atZone(zoneId).toOffsetDateTime()

        return DarkWindow(
            startIso = start.toString(),
            endIso = end.toString(),
            description = "Approximate dark window from 20:00 to 03:00 local time."
        )
    }

    /**
     * 🌕 placeholder: Looks at the day-of-month
     */
    override fun describeMoonPhase(
        dateTime: OffsetDateTime,
        latitude: Double,
        longitude: Double
    ): String {
        val day = dateTime.dayOfMonth

        return when (day) {
            in 1..3 -> "New-ish moon, very dark skies."
            in 4..10 -> "Waxing moon, moderate darkness."
            in 11..17 -> "Near full moon, bright skies."
            in 18..24 -> "Waning moon, improving darkness."
            else -> "Darkening moon, good for deep sky."
        }
    }

    /**
     * 🌌 placeholder: Just says “around midnight”
     */
    override fun bestMilkyWayTimeHint(
        date: LocalDate,
        latitude: Double,
        longitude: Double
    ): String {
        return "Milky Way best viewed around local midnight if seasonally visible."
    }
}