package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.calendar.AstroCalendarService
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import com.github.ioj0230.astro.infra.task.InMemoryTaskRepository
import com.github.ioj0230.astro.infra.task.InMemoryTaskRunRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskRunnerFailureTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)
    private val notifier = FakeNotifier()
    private val repo = InMemoryTaskRepository()
    private val runner =
        TaskRunner(
            taskRepository = repo,
            taskRunRepository = InMemoryTaskRunRepository(),
            astroMathService = DummyAstroMathService(),
            astroEventService = DummyAstroEventProvider(),
            skySummaryService = SkySummaryService(DummyAstroMathService(), DummyAstroEventProvider()),
            astroCalendarService = AstroCalendarService(emptyList()),
            json = Json,
            notificationService = NotificationService(listOf(notifier)),
            clock = clock,
        )

    private fun brokenTask(notify: NotifyPolicy) =
        runner.createTask(
            name = "Broken dark window",
            type = TaskType.DARK_WINDOW,
            // Bad date -> LocalDate.parse throws inside the dark-window run.
            payload = DarkWindowRequest(10.3, 123.9, "not-a-date", "Asia/Manila"),
            payloadSerializer = DarkWindowRequest.serializer(),
            frequency = TaskFrequency.DAILY,
            preferredHourUtc = 0,
            notify = notify,
        )

    private fun healthyTask(notify: NotifyPolicy) =
        runner.createTask(
            name = "Meteor alert",
            type = TaskType.METEOR_ALERT,
            payload = MeteorAlertRequest(10.3, 123.9, "2026-12-10", "Asia/Manila"),
            payloadSerializer = MeteorAlertRequest.serializer(),
            frequency = TaskFrequency.DAILY,
            preferredHourUtc = 0,
            notify = notify,
        )

    @Test
    fun `one throwing task is recorded as FAILED and does not abort the rest of the tick`() =
        runBlocking {
            // Before the unified run path, the broken task's exception
            // escaped runAllEnabled and the healthy task never ran.
            val broken = brokenTask(NotifyPolicy.NEVER)
            val healthy = healthyTask(NotifyPolicy.NEVER)

            val results = runner.runAllEnabled().associateBy { it.task.id }

            assertEquals(TaskStatus.FAILED, results.getValue(broken.id).task.lastStatus)
            assertNotNull(results.getValue(broken.id).task.lastError)
            assertEquals(TaskStatus.SUCCESS, results.getValue(healthy.id).task.lastStatus)
            assertEquals(TaskStatus.FAILED, repo.findById(broken.id)!!.lastStatus)
        }

    @Test
    fun `ON_FAILURE sends the error but stays quiet on success`() =
        runBlocking {
            brokenTask(NotifyPolicy.ON_FAILURE)
            healthyTask(NotifyPolicy.ON_FAILURE)

            runner.runAllEnabled()

            val message = notifier.sent.single()
            assertTrue(message.title.startsWith("⚠️ Task failed — Broken dark window"), message.title)
            assertTrue(message.body.contains("not-a-date"), message.body)
        }

    @Test
    fun `ALWAYS sends both the success result and the failure`() =
        runBlocking {
            brokenTask(NotifyPolicy.ALWAYS)
            healthyTask(NotifyPolicy.ALWAYS)

            runner.runAllEnabled()

            val titles = notifier.sent.map { it.title }.sorted()
            assertEquals(2, titles.size, titles.toString())
            assertTrue(titles.any { it.startsWith("☄️ Meteor alert — Meteor alert") }, titles.toString())
            assertTrue(titles.any { it.startsWith("⚠️ Task failed") }, titles.toString())
        }

    @Test
    fun `NEVER sends nothing even when the task fails`() =
        runBlocking {
            brokenTask(NotifyPolicy.NEVER)

            val result = runner.runAllEnabled().single()

            assertEquals(TaskStatus.FAILED, result.task.lastStatus)
            assertTrue(result.deliveries.isEmpty())
            assertTrue(notifier.sent.isEmpty())
        }
}
