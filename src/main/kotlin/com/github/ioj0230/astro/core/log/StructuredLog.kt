package com.github.ioj0230.astro.core.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.spi.LoggingEventBuilder
import kotlin.reflect.KClass

/**
 * The one way this codebase writes logs: an event name from [LogEvents],
 * a human-readable message, and key/value fields.
 *
 * ```
 * log.warn(LogEvents.NOTIFICATION_DELIVERY_FAILED, "Telegram rejected the message",
 *     "taskId" to task.id, "channel" to "telegram", "error" to error)
 * ```
 *
 * In production each call becomes one JSON line on stdout
 * (`infra.logging.CloudLoggingJsonLayout`), which Cloud Logging indexes, so
 * every field is queryable: `jsonPayload.event="task.run.finished"
 * jsonPayload.taskId="..."`. The request id of the HTTP call being served
 * is added automatically. Null fields are dropped.
 *
 * Never pass secrets (webhook URLs, tokens, passwords) as fields or in the
 * message. Logs are kept for 30 days and readable by anyone with log access.
 *
 * Only depends on the slf4j API, so `core` can use it without knowing
 * about Logback or Cloud Logging.
 */
class StructuredLog(
    private val logger: Logger,
) {
    fun info(
        event: String,
        message: String,
        vararg fields: Pair<String, Any?>,
    ) = write(logger.atInfo(), event, message, fields, cause = null)

    fun warn(
        event: String,
        message: String,
        vararg fields: Pair<String, Any?>,
        cause: Throwable? = null,
    ) = write(logger.atWarn(), event, message, fields, cause)

    fun error(
        event: String,
        message: String,
        vararg fields: Pair<String, Any?>,
        cause: Throwable? = null,
    ) = write(logger.atError(), event, message, fields, cause)

    private fun write(
        builder: LoggingEventBuilder,
        event: String,
        message: String,
        fields: Array<out Pair<String, Any?>>,
        cause: Throwable?,
    ) {
        var b = builder.addKeyValue(EVENT_FIELD, event)
        for ((key, value) in fields) {
            if (value != null) b = b.addKeyValue(key, value)
        }
        if (cause != null) b = b.setCause(cause)
        b.log(message)
    }

    companion object {
        const val EVENT_FIELD = "event"

        fun of(owner: KClass<*>): StructuredLog = StructuredLog(LoggerFactory.getLogger(owner.java))
    }
}

/**
 * Every event name in one place, so the set of things worth querying is
 * discoverable and names don't drift ("task.run.finished" vs
 * "task_run_done"). Format: `area.thing.what_happened`, lowercase.
 */
object LogEvents {
    const val APP_STARTED = "app.started"
    const val APP_CRASHED = "app.crashed"
    const val REQUEST_UNHANDLED_ERROR = "request.unhandled_error"
    const val TASK_RUN_FINISHED = "task.run.finished"
    const val TASK_RUN_HISTORY_WRITE_FAILED = "task.run.history_write_failed"
    const val NOTIFICATION_DELIVERY_FAILED = "notification.delivery.failed"
    const val NOTIFICATION_TEST_SENT = "notification.test_sent"
}
