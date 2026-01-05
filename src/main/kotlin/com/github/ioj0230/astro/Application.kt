package com.github.ioj0230.astro

import com.github.ioj0230.astro.api.darkWindowRoute
import com.github.ioj0230.astro.api.meteorAlertRoute
import com.github.ioj0230.astro.api.skySummaryRoute
import com.github.ioj0230.astro.api.task.darkWindowTaskRoute
import com.github.ioj0230.astro.api.task.meteorAlertTaskRoute
import com.github.ioj0230.astro.api.taskRoute
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.core.task.TaskRepository
import com.github.ioj0230.astro.core.task.TaskRunner
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import com.github.ioj0230.astro.infra.task.FirestoreTaskRepository
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module)
        .start(wait = true)
}

data class ServiceRegistry(
    val astroMathService: AstroMathService,
    val astroEventService: AstroEventService,
    val skySummaryService: SkySummaryService,
    val taskRepository: TaskRepository,
    val taskRunner: TaskRunner,
    val json: Json,
)

fun Application.module() {
    val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    val astroMathService: AstroMathService = DummyAstroMathService()
    val astroEventService: AstroEventService = DummyAstroEventProvider()
    val skySummaryService = SkySummaryService(astroMathService, astroEventService)

    val firestore = FirestoreOptions.getDefaultInstance().service
    val taskRepository = FirestoreTaskRepository(firestore, json)
    val taskRunner =
        TaskRunner(
            taskRepository,
            astroMathService,
            astroEventService,
            skySummaryService,
            json,
        )

    val services =
        ServiceRegistry(
            astroMathService = astroMathService,
            astroEventService = astroEventService,
            skySummaryService = skySummaryService,
            taskRepository = taskRepository,
            taskRunner = taskRunner,
            json,
        )

    install(CallLogging)
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            },
        )
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }

        // run-now APIs
        darkWindowRoute(services)
        meteorAlertRoute(services)
        skySummaryRoute(services)

        // task APIs
        taskRoute(services)
        darkWindowTaskRoute(services)
        meteorAlertTaskRoute(services)
    }
}
