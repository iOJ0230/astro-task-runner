package com.github.ioj0230.astro.core.log

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/** One captured log call, with its MDC and fields read at log time. */
data class CapturedLog(
    val level: String,
    val message: String,
    val fields: Map<String, Any?>,
    val mdc: Map<String, String>,
    val cause: String?,
) {
    val event: Any? get() = fields[StructuredLog.EVENT_FIELD]
}

/**
 * Captures what a class logs, for asserting on structured fields.
 * Reads the MDC inside `append`, on the logging thread, because it's
 * thread-local and would be gone by the time the test inspects it.
 */
class LogCapture(owner: KClass<*>) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(owner.java) as Logger
    private val captured = mutableListOf<CapturedLog>()

    private val appender =
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                synchronized(captured) {
                    captured +=
                        CapturedLog(
                            level = event.level.toString(),
                            message = event.formattedMessage,
                            fields = event.keyValuePairs.orEmpty().associate { it.key to it.value },
                            mdc = event.mdcPropertyMap.orEmpty().toMap(),
                            cause = event.throwableProxy?.message,
                        )
                }
            }
        }.also {
            it.context = logger.loggerContext
            it.start()
            logger.addAppender(it)
        }

    val logs: List<CapturedLog> get() = synchronized(captured) { captured.toList() }

    fun withEvent(event: String) = logs.filter { it.event == event }

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
    }
}
