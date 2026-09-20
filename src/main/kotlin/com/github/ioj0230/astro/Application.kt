package com.github.ioj0230.astro

import com.github.ioj0230.astro.api.darkWindowRoute
import com.github.ioj0230.astro.api.meteorAlertRoute
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
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
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.DateTimeException

fun main() {
    embeddedServer(Netty, port = 8080) { module() }
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

/**
 * @param taskRepositoryOverride Injection point used by tests to avoid
 * touching real Firestore (which needs live GCP credentials). Production
 * (`main()`) always leaves this null and gets a Firestore-backed
 * repository. See `testModule()` (test sourceSet) and CLAUDE.md
 * "Resolved" #1.
 */
fun Application.module(taskRepositoryOverride: TaskRepository? = null) {
    val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    val astroMathService: AstroMathService = DummyAstroMathService()
    val astroEventService: AstroEventService = DummyAstroEventProvider()
    val skySummaryService = SkySummaryService(astroMathService, astroEventService)

    val taskRepository =
        taskRepositoryOverride ?: run {
            val firestore = FirestoreOptions.getDefaultInstance().service
            FirestoreTaskRepository(firestore, json)
        }
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
    install(StatusPages) {
        // Client input errors -> 400, instead of falling through to a 500.
        // TaskRunner's require() checks throw IllegalArgumentException;
        // LocalDate.parse / ZoneId.of throw DateTimeException; malformed
        // JSON bodies throw JsonConvertException.
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorBody(ApiError(code = "INVALID_REQUEST", message = cause.message ?: "Invalid request")),
            )
        }
        exception<DateTimeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorBody(
                    ApiError(code = "INVALID_DATE_OR_TIMEZONE", message = cause.message ?: "Invalid date or time zone"),
                ),
            )
        }
        exception<JsonConvertException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorBody(ApiError(code = "MALFORMED_REQUEST_BODY", message = cause.message ?: "Malformed request body")),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorBody(ApiError(code = "INTERNAL_ERROR", message = "Something went wrong")),
            )
        }
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
