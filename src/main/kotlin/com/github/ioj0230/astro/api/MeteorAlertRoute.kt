package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.meteorAlertRoute(services: ServiceRegistry) {
    post("/api/run/astro/meteor-alert") {
        val request = call.receive<MeteorAlertRequest>()
        val response: MeteorAlertResponse =
            services.astroEventService.upcomingMeteorShowers(request)

        call.respond(response)
    }
}
