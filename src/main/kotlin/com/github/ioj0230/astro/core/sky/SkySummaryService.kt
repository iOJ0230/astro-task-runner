package com.github.ioj0230.astro.core.sky

import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import java.time.LocalDate

class SkySummaryService(
    private val astroMathService: AstroMathService,
    private val astroEventService: AstroEventService,
) {
    fun buildSummary(request: SkySummaryRequest): SkySummaryResponse {
        val date = LocalDate.parse(request.dateIso)

        // 1. Dark window
        val darkWindow =
            astroMathService.computeDarkWindow(
                latitude = request.latitude,
                longitude = request.longitude,
                date = date,
                timeZoneId = request.timeZoneId,
            )

        // 2. Meteor showers
        val meteorResponse =
            astroEventService.upcomingMeteorShowers(
                MeteorAlertRequest(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    dateIso = request.dateIso,
                    timeZoneId = request.timeZoneId,
                ),
            )

        val meteors = meteorResponse.events

        // 3. Overall summary text
        val summary =
            buildString {
                append("Dark window from ${darkWindow.startIso} to ${darkWindow.endIso}. ")
                if (meteors.isEmpty()) {
                    append("No major meteor showers from dummy data near this date. ")
                } else {
                    append("Meteor activity: ")
                    append(meteors.joinToString { it.name })
                    append(". ")
                }
            }.trim()

        return SkySummaryResponse(
            dateIso = request.dateIso,
            timeZoneId = request.timeZoneId,
            darkWindow = darkWindow,
            meteors = meteors,
            overallSummary = summary,
            notes = "Summary uses dummy math and dummy meteor data; real astro APIs can be plugged in later.",
        )
    }
}
