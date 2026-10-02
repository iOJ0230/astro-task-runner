package com.github.ioj0230.astro.core.log

import kotlin.test.Test
import kotlin.test.assertEquals

class StructuredLogTest {
    private class Owner

    @Test
    fun `writes the event name and fields, dropping nulls`() {
        LogCapture(Owner::class).use { capture ->
            StructuredLog.of(Owner::class).info(
                LogEvents.TASK_RUN_FINISHED,
                "Task 'Sky' succeeded",
                "taskId" to "t-1",
                "durationMs" to 42L,
                "error" to null,
            )

            val log = capture.logs.single()
            assertEquals("INFO", log.level)
            assertEquals("Task 'Sky' succeeded", log.message)
            assertEquals(mapOf("event" to "task.run.finished", "taskId" to "t-1", "durationMs" to 42L), log.fields)
        }
    }

    @Test
    fun `warn and error carry the cause`() {
        LogCapture(Owner::class).use { capture ->
            val log = StructuredLog.of(Owner::class)

            log.warn(LogEvents.NOTIFICATION_DELIVERY_FAILED, "failed", "channel" to "email")
            log.error(LogEvents.TASK_RUN_HISTORY_WRITE_FAILED, "boom", cause = IllegalStateException("Firestore down"))

            assertEquals(listOf("WARN", "ERROR"), capture.logs.map { it.level })
            assertEquals(null, capture.logs[0].cause)
            assertEquals("Firestore down", capture.logs[1].cause)
        }
    }
}
