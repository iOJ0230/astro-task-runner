package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.sky.SkySummaryRequest
import com.github.ioj0230.astro.core.sky.SkySummaryResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.skySummaryRoute(services: ServiceRegistry) {
    post("/api/run/astro/sky-summary") {
        val request = call.receive<SkySummaryRequest>()
        val response: SkySummaryResponse =
            services.skySummaryService.buildSummary(request)

        call.respond(response)
    }
}
