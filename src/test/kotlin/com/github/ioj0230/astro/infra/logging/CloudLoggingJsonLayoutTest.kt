package com.github.ioj0230.astro.infra.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.event.KeyValuePair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudLoggingJsonLayoutTest {
    private val logger = LoggerContext().getLogger("com.github.ioj0230.astro.Test")

    private fun render(
        level: Level,
        message: String = "hello",
        fields: Map<String, Any?> = emptyMap(),
        mdc: Map<String, String> = emptyMap(),
        cause: Throwable? = null,
        projectId: String? = null,
    ): JsonObject {
        val event = LoggingEvent("fqcn", logger, level, message, cause, null)
        fields.forEach { (k, v) -> event.addKeyValuePair(KeyValuePair(k, v)) }
        event.mdcPropertyMap = mdc
        val layout = CloudLoggingJsonLayout().also { it.projectId = projectId }
        val line = layout.doLayout(event)
        assertTrue(line.endsWith("\n") && line.count { it == '\n' } == 1, "exactly one line per event")
        return Json.parseToJsonElement(line).jsonObject
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `severity uses Cloud Logging names`() {
        assertEquals("INFO", render(Level.INFO).str("severity"))
        assertEquals("WARNING", render(Level.WARN).str("severity"))
        assertEquals("ERROR", render(Level.ERROR).str("severity"))
        assertEquals("DEBUG", render(Level.DEBUG).str("severity"))
    }

    @Test
    fun `fields and MDC become top-level, with numbers kept as numbers`() {
        val json =
            render(
                Level.INFO,
                fields = mapOf("event" to "task.run.finished", "taskId" to "t-1", "durationMs" to 42L, "status" to "SUCCESS"),
                mdc = mapOf("requestId" to "abc123"),
            )

        assertEquals("hello", json.str("message"))
        assertEquals("task.run.finished", json.str("event"))
        assertEquals("t-1", json.str("taskId"))
        assertEquals(42L, json["durationMs"]!!.jsonPrimitive.long)
        assertEquals("abc123", json.str("requestId"))
    }

    @Test
    fun `custom fields cannot overwrite the standard ones`() {
        val json = render(Level.WARN, fields = mapOf("severity" to "INFO", "message" to "spoofed"))

        assertEquals("WARNING", json.str("severity"))
        assertEquals("hello", json.str("message"))
    }

    @Test
    fun `exceptions go to stack_trace, keeping the message clean`() {
        val json = render(Level.ERROR, message = "Unhandled exception", cause = IllegalStateException("Firestore down"))

        assertEquals("Unhandled exception", json.str("message"))
        assertTrue(json.str("stack_trace")!!.contains("IllegalStateException: Firestore down"))
    }

    @Test
    fun `trace field links the request when the project is known`() {
        val withProject = render(Level.INFO, mdc = mapOf("requestId" to "abc123"), projectId = "my-proj")
        assertEquals("projects/my-proj/traces/abc123", withProject.str(CloudLoggingJsonLayout.TRACE_FIELD))

        assertNull(render(Level.INFO, mdc = mapOf("requestId" to "abc123")).str(CloudLoggingJsonLayout.TRACE_FIELD))
        assertFalse(render(Level.INFO, projectId = "my-proj").containsKey(CloudLoggingJsonLayout.TRACE_FIELD))
    }

    @Test
    fun `messages with quotes and newlines stay one valid JSON line`() {
        val json = render(Level.INFO, message = "line one\nsaid \"hi\"")

        assertEquals("line one\nsaid \"hi\"", json.str("message"))
    }
}
