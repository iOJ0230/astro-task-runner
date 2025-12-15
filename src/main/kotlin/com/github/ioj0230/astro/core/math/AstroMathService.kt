package com.github.ioj0230.astro.core.math

import com.github.ioj0230.astro.core.darkwindow.DarkWindow
import java.time.LocalDate
import java.time.OffsetDateTime

interface AstroMathService {
    fun computeDarkWindow(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        timeZoneId: String,
    ): DarkWindow

    fun describeMoonPhase(
        dateTime: OffsetDateTime,
        latitude: Double,
        longitude: Double,
    ): String

    fun bestMilkyWayTimeHint(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): String
}
