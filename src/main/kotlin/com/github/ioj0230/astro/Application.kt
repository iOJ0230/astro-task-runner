package com.github.ioj0230.astro

import com.github.ioj0230.astro.core.astro.AstroEventService
import com.github.ioj0230.astro.core.astro.AstroMathService
import com.github.ioj0230.astro.core.astro.DummyAstroEventProvider
import com.github.ioj0230.astro.core.astro.DummyAstroMathService
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.meteors.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteors.MeteorAlertResponse
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module)
        .start(wait = true)
}

data class ServiceRegistry(
    val astroMathService: AstroMathService,
    val astroEventService: AstroEventService
)

fun Application.module() {

    val services = ServiceRegistry(
        astroMathService = DummyAstroMathService(),
        astroEventService = DummyAstroEventProvider()
    )

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; ignoreUnknownKeys = true })
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }

        post("/api/run/astro/dark-window") {
            val request = call.receive<DarkWindowRequest>()
            val date = LocalDate.parse(request.dateIso)

            val window = services.astroMathService.computeDarkWindow(
                latitude = request.latitude,
                longitude = request.longitude,
                date = date,
                timeZoneId = request.timeZoneId
            )

            val response = DarkWindowResponse(
                window = window,
                notes = "Moon + Milky Way hints can be added later."
            )

            call.respond(response)
        }

        post("/api/run/astro/meteor-alert") {
            val request = call.receive<MeteorAlertRequest>()

            val response: MeteorAlertResponse =
                services.astroEventService.upcomingMeteorShowers(request)

            call.respond(response)
        }
    }
}