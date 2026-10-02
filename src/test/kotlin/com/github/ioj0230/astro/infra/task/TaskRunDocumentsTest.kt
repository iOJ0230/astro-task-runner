package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.task.TaskRun
import com.github.ioj0230.astro.core.task.TaskRunTrigger
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.core.task.TaskType
import com.google.cloud.Timestamp
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskRunDocumentsTest {
    private val run =
        TaskRun(
            id = "run-1",
            taskId = "task-1",
            taskName = "Weekly sky",
            taskType = TaskType.ASTRO_CALENDAR,
            trigger = TaskRunTrigger.TICK,
            startedAtIso = "2026-10-09T00:00Z",
            finishedAtIso = "2026-10-09T00:00:02.5Z",
            status = TaskStatus.FAILED,
            error = "All calendar sources failed",
            outputJson = null,
            deliveries = listOf(NotificationDelivery("email", false, "auth failed")),
            requestId = "105445aa7843bc8bf206b12000100000",
        )

    @Test
    fun `fields round-trip back to the same run`() {
        val fields = TaskRunDocuments.toFields(run, Json)

        assertEquals(run, TaskRunDocuments.fromFields(fields, Json))
    }

    @Test
    fun `ordering and TTL fields are real timestamps, expiring after the retention period`() {
        val fields = TaskRunDocuments.toFields(run, Json)

        assertEquals(Timestamp.parseTimestamp("2026-10-09T00:00:00Z"), fields["startedAt"])
        assertEquals(Timestamp.parseTimestamp("2027-01-07T00:00:00Z"), fields["expireAt"])
        assertEquals("FAILED", fields["status"])
    }
}
