package com.github.ioj0230.astro

import com.github.ioj0230.astro.api.darkWindowRoute
import com.github.ioj0230.astro.api.meteorAlertRoute
import com.github.ioj0230.astro.api.skySummaryRoute
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module)
        .start(wait = true)
}

data class ServiceRegistry(
    val astroMathService: AstroMathService,
    val astroEventService: AstroEventService,
    val skySummaryService: SkySummaryService,
)

fun Application.module() {
    val astroMathService: AstroMathService = DummyAstroMathService()
    val astroEventService: AstroEventService = DummyAstroEventProvider()
    val skySummaryService = SkySummaryService(astroMathService, astroEventService)

    val services = ServiceRegistry(
        astroMathService = astroMathService,
        astroEventService = astroEventService,
        skySummaryService = skySummaryService
    )

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; ignoreUnknownKeys = true })
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }

        darkWindowRoute(services)
        meteorAlertRoute(services)
        skySummaryRoute(services)
    }
}