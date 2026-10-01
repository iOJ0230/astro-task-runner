package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.calendar.AstroCalendarRequest
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /api/calendar/events?timeZoneId=Asia/Manila&startDate=2026-10-01&days=31`
 *
 * A plain read, so it's a GET with query parameters in the resource style
 * docs/CONVENTIONS.md asks new endpoints to use (not the `/api/run/astro/...` family).
 * `startDate` defaults to today in `timeZoneId`; `days` defaults to 7.
 */
fun Route.astroCalendarRoute(services: ServiceRegistry) {
    get("/api/calendar/events") {
        val params = call.request.queryParameters
        val timeZoneId = requireNotNull(params["timeZoneId"]) { "timeZoneId query parameter is required" }
        val days =
            params["days"]?.let {
                requireNotNull(it.toIntOrNull()) { "days must be a number, got '$it'" }
            } ?: AstroCalendarRequest(timeZoneId = timeZoneId).days

        val request =
            AstroCalendarRequest(
                timeZoneId = timeZoneId,
                startDateIso = params["startDate"],
                days = days,
            )

        call.respond(services.astroCalendarService.upcoming(request))
    }
}
