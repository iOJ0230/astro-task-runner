package com.github.ioj0230.astro.infra.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.LayoutBase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * Renders each log event as one JSON line in the shape Cloud Logging
 * understands when it reads a Cloud Run container's stdout:
 *
 * - `severity`: mapped to Cloud Logging's names (WARN → WARNING). Without
 *   this, plain-text lines get no severity and `severity>=ERROR` alerts
 *   never fire.
 * - `message`, `timestamp`, `logger`, `thread`.
 * - every MDC entry (e.g. `requestId`) and every key/value field from
 *   `StructuredLog`, as top-level fields → `jsonPayload.<name>` in queries.
 * - `stack_trace` for exceptions, which Error Reporting picks up.
 * - `logging.googleapis.com/trace` when the request id and the project id
 *   are both known, which groups a request's lines under its request log
 *   entry in Logs Explorer.
 *
 * Wired up in `src/main/resources/logback.xml`. Tests use plain text
 * (`src/test/resources/logback-test.xml`) for readability.
 */
class CloudLoggingJsonLayout : LayoutBase<ILoggingEvent>() {
    /** GCP project id for the trace field. Cloud Run doesn't set this env var by itself; see docs/SETUP.md. */
    var projectId: String? = System.getenv("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() }

    override fun doLayout(event: ILoggingEvent): String {
        val fields = LinkedHashMap<String, JsonElement>()
        fields["timestamp"] = JsonPrimitive(Instant.ofEpochMilli(event.timeStamp).toString())
        fields["severity"] = JsonPrimitive(severity(event.level))
        fields["message"] = JsonPrimitive(event.formattedMessage ?: "")
        fields["logger"] = JsonPrimitive(event.loggerName)
        fields["thread"] = JsonPrimitive(event.threadName)

        // Custom fields never overwrite the standard ones above.
        event.mdcPropertyMap?.forEach { (key, value) -> if (key !in fields) fields[key] = JsonPrimitive(value) }
        event.keyValuePairs?.forEach { pair -> if (pair.key !in fields) fields[pair.key] = toJson(pair.value) }

        event.throwableProxy?.let { fields["stack_trace"] = JsonPrimitive(ThrowableProxyUtil.asString(it)) }

        val requestId = event.mdcPropertyMap?.get(REQUEST_ID_MDC_KEY)
        val project = projectId
        if (requestId != null && project != null) {
            fields[TRACE_FIELD] = JsonPrimitive("projects/$project/traces/$requestId")
        }

        return JsonObject(fields).toString() + CoreConstants.LINE_SEPARATOR
    }

    private fun severity(level: Level): String =
        when (level.toInt()) {
            Level.ERROR_INT -> "ERROR"
            Level.WARN_INT -> "WARNING"
            Level.INFO_INT -> "INFO"
            else -> "DEBUG"
        }

    private fun toJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonPrimitive(null as String?)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }

    companion object {
        /** Set by Ktor's CallLogging (`callIdMdc`) for every HTTP request. */
        const val REQUEST_ID_MDC_KEY = "requestId"
        const val TRACE_FIELD = "logging.googleapis.com/trace"
    }
}
