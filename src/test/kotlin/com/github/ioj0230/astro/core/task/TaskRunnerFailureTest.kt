package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import com.github.ioj0230.astro.infra.task.InMemoryTaskRepository
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskRunnerFailureTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)
    private val repo = InMemoryTaskRepository()
    private val runner =
        TaskRunner(
            taskRepository = repo,
            astroMathService = DummyAstroMathService(),
            astroEventService = DummyAstroEventProvider(),
            skySummaryService = SkySummaryService(DummyAstroMathService(), DummyAstroEventProvider()),
            json = Json,
            clock = clock,
        )

    @Test
    fun `one throwing task is recorded as FAILED and does not abort the rest of the tick`() {
        // Bad date -> LocalDate.parse throws inside the dark-window run.
        // Before the unified run path, this escaped runAllEnabled and the
        // meteor-alert task below never ran.
        val broken =
            runner.createTask(
                name = "Broken dark window",
                type = TaskType.DARK_WINDOW,
                payload = DarkWindowRequest(10.3, 123.9, "not-a-date", "Asia/Manila"),
                payloadSerializer = DarkWindowRequest.serializer(),
                frequency = TaskFrequency.DAILY,
                preferredHourUtc = 0,
            )
        val healthy =
            runner.createTask(
                name = "Meteor alert",
                type = TaskType.METEOR_ALERT,
                payload = MeteorAlertRequest(10.3, 123.9, "2026-12-10", "Asia/Manila"),
                payloadSerializer = MeteorAlertRequest.serializer(),
                frequency = TaskFrequency.DAILY,
                preferredHourUtc = 0,
            )

        val results = runner.runAllEnabled().associateBy { it.task.id }

        assertEquals(TaskStatus.FAILED, results.getValue(broken.id).task.lastStatus)
        assertNotNull(results.getValue(broken.id).task.lastError)
        assertEquals(TaskStatus.SUCCESS, results.getValue(healthy.id).task.lastStatus)
        assertEquals(TaskStatus.FAILED, repo.findById(broken.id)!!.lastStatus)
    }
}
