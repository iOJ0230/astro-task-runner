package com.github.ioj0230.astro

import com.github.ioj0230.astro.api.astroCalendarRoute
import com.github.ioj0230.astro.api.darkWindowRoute
import com.github.ioj0230.astro.api.meteorAlertRoute
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import com.github.ioj0230.astro.api.notify.notificationRoute
import com.github.ioj0230.astro.api.skySummaryRoute
import com.github.ioj0230.astro.api.task.astroCalendarTaskRoute
import com.github.ioj0230.astro.api.task.darkWindowTaskRoute
import com.github.ioj0230.astro.api.task.meteorAlertTaskRoute
import com.github.ioj0230.astro.api.taskRoute
import com.github.ioj0230.astro.core.calendar.AstroCalendarService
import com.github.ioj0230.astro.core.log.LogEvents
import com.github.ioj0230.astro.core.log.StructuredLog
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.notify.NotificationService
import com.github.ioj0230.astro.core.notify.Notifier
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.core.task.TaskRepository
import com.github.ioj0230.astro.core.task.TaskRunRepository
import com.github.ioj0230.astro.core.task.TaskRunner
import com.github.ioj0230.astro.infra.calendar.DummyAstroCalendarProvider
import com.github.ioj0230.astro.infra.logging.CloudLoggingJsonLayout
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import com.github.ioj0230.astro.infra.notify.NotifierFactory
import com.github.ioj0230.astro.infra.task.FirestoreTaskRepository
import com.github.ioj0230.astro.infra.task.FirestoreTaskRunRepository
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.util.UUID

fun main() {
    // A crash (e.g. bad config at startup) would otherwise print a raw,
    // multi-line stack trace that Cloud Logging splits into dozens of
    // unsearchable entries. Route it through the JSON log instead.
    Thread.setDefaultUncaughtExceptionHandler { thread, cause ->
        appLog.error(LogEvents.APP_CRASHED, "Uncaught exception on thread ${thread.name}", cause = cause)
    }
    embeddedServer(Netty, port = 8080) { module() }
        .start(wait = true)
}

private const val CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context"
private const val REQUEST_ID_HEADER = "X-Request-Id"

private val appLog = StructuredLog.of(ServiceRegistry::class)

data class ServiceRegistry(
    val astroMathService: AstroMathService,
    val astroEventService: AstroEventService,
    val skySummaryService: SkySummaryService,
    val astroCalendarService: AstroCalendarService,
    val notificationService: NotificationService,
    val taskRepository: TaskRepository,
    val taskRunRepository: TaskRunRepository,
    val taskRunner: TaskRunner,
    val json: Json,
)

/**
 * @param taskRepositoryOverride Injection point used by tests to avoid
 * touching real Firestore (which needs live GCP credentials). Production
 * (`main()`) always leaves this null and gets a Firestore-backed
 * repository. See `testModule()` (test sourceSet) and CLAUDE.md
 * "Resolved" #1.
 * @param taskRunRepositoryOverride Same, for run history.
 * @param notifiersOverride Same idea for notifications: tests pass an
 * explicit list (usually empty or fakes) so a developer's own
 * DISCORD_WEBHOOK_URL etc. never gets messaged by a test run. Null →
 * channels come from environment variables via [NotifierFactory].
 */
fun Application.module(
    taskRepositoryOverride: TaskRepository? = null,
    taskRunRepositoryOverride: TaskRunRepository? = null,
    notifiersOverride: List<Notifier>? = null,
) {
    val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    val astroMathService: AstroMathService = DummyAstroMathService()
    val astroEventService: AstroEventService = DummyAstroEventProvider()
    val skySummaryService = SkySummaryService(astroMathService, astroEventService)
    val astroCalendarService = AstroCalendarService(listOf(DummyAstroCalendarProvider()))

    val notifiers =
        notifiersOverride ?: run {
            val httpClient =
                HttpClient(CIO) {
                    install(HttpTimeout) { requestTimeoutMillis = 10_000 }
                }
            environment.monitor.subscribe(ApplicationStopped) { httpClient.close() }
            NotifierFactory.fromEnvironment(System.getenv(), httpClient)
        }
    val notificationService = NotificationService(notifiers)

    // Only touch Firestore (and its credentials) if something needs it.
    val firestore by lazy { FirestoreOptions.getDefaultInstance().service }
    val taskRepository = taskRepositoryOverride ?: FirestoreTaskRepository(firestore, json)
    val taskRunRepository = taskRunRepositoryOverride ?: FirestoreTaskRunRepository(firestore, json)
    val taskRunner =
        TaskRunner(
            taskRepository = taskRepository,
            taskRunRepository = taskRunRepository,
            astroMathService = astroMathService,
            astroEventService = astroEventService,
            skySummaryService = skySummaryService,
            astroCalendarService = astroCalendarService,
            json = json,
            notificationService = notificationService,
        )

    val services =
        ServiceRegistry(
            astroMathService = astroMathService,
            astroEventService = astroEventService,
            skySummaryService = skySummaryService,
            astroCalendarService = astroCalendarService,
            notificationService = notificationService,
            taskRepository = taskRepository,
            taskRunRepository = taskRunRepository,
            taskRunner = taskRunner,
            json = json,
        )

    // Every request gets an id: Cloud Run's trace id when it sent one
    // (X-Cloud-Trace-Context: TRACE_ID/SPAN_ID;o=1), otherwise a new one.
    // It's put in the MDC as "requestId" (so every log line written while
    // serving the request carries it), echoed back as X-Request-Id, and
    // stored on any TaskRun the request causes.
    install(CallId) {
        retrieve { call -> call.request.header(CLOUD_TRACE_HEADER)?.substringBefore('/') }
        generate { UUID.randomUUID().toString().replace("-", "") }
        // Header values end up in logs; only accept plain ids.
        verify { id -> id.length in 1..64 && id.all { it.isLetterOrDigit() || it == '-' } }
        replyToHeader(REQUEST_ID_HEADER)
    }
    install(CallLogging) {
        callIdMdc(CloudLoggingJsonLayout.REQUEST_ID_MDC_KEY)
        // ANSI colors would end up as escape codes inside the JSON message.
        disableDefaultColors()
    }
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
            appLog.error(
                LogEvents.REQUEST_UNHANDLED_ERROR,
                "Unhandled exception",
                "method" to call.request.httpMethod.value,
                "path" to call.request.path(),
                cause = cause,
            )
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

        // read APIs
        astroCalendarRoute(services)

        // notification APIs
        notificationRoute(services)

        // task APIs
        taskRoute(services)
        darkWindowTaskRoute(services)
        meteorAlertTaskRoute(services)
        astroCalendarTaskRoute(services)
    }

    // Last, so it only appears once everything above (Firestore included) is up.
    appLog.info(
        LogEvents.APP_STARTED,
        "Started; notification channels: ${notificationService.channels.ifEmpty { listOf("none") }}",
        "notificationChannels" to notificationService.channels.joinToString().ifEmpty { "none" },
    )
}
