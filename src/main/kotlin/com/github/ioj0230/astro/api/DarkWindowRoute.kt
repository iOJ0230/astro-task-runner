package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.LocalDate

fun Route.darkWindowRoute(services: ServiceRegistry) {
    post("/api/run/astro/dark-window") {
        val request = call.receive<DarkWindowRequest>()
        val date = LocalDate.parse(request.dateIso)

        val window =
            services.astroMathService.computeDarkWindow(
                latitude = request.latitude,
                longitude = request.longitude,
                date = date,
                timeZoneId = request.timeZoneId,
            )

        val response =
            DarkWindowResponse(
                window = window,
                notes = "Moon + Milky Way hints can be added later.",
            )

        call.respond(response)
    }
}
