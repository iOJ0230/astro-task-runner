package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.calendar.AstroCalendarService
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationDelivery
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRunHistoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)
    private val tasks = InMemoryTaskRepository()
    private val runs = InMemoryTaskRunRepository()

    private fun runner(
        runRepository: TaskRunRepository = runs,
        notifiers: List<FakeNotifier> = emptyList(),
    ) = TaskRunner(
        taskRepository = tasks,
        taskRunRepository = runRepository,
        astroMathService = DummyAstroMathService(),
        astroEventService = DummyAstroEventProvider(),
        skySummaryService = SkySummaryService(DummyAstroMathService(), DummyAstroEventProvider()),
        astroCalendarService = AstroCalendarService(emptyList()),
        json = Json,
        notificationService = NotificationService(notifiers),
        clock = clock,
    )

    private fun TaskRunner.meteorTask(
        frequency: TaskFrequency = TaskFrequency.MANUAL,
        enabled: Boolean = true,
    ) = createTask(
        name = "Meteor alert",
        type = TaskType.METEOR_ALERT,
        payload = MeteorAlertRequest(10.3, 123.9, "2026-12-10", "Asia/Manila"),
        payloadSerializer = MeteorAlertRequest.serializer(),
        frequency = frequency,
        preferredHourUtc = if (frequency == TaskFrequency.DAILY) 0 else null,
        enabled = enabled,
    )

    @Test
    fun `a successful manual run is recorded with its output`() =
        runBlocking {
            val runner = runner()
            val task = runner.meteorTask()

            val result = runner.runTask(task.id)!!

            val run = runs.findByTaskId(task.id, 10).single()
            assertEquals(result.runId, run.id)
            assertEquals(TaskStatus.SUCCESS, run.status)
            assertEquals(TaskRunTrigger.MANUAL, run.trigger)
            assertEquals("Meteor alert", run.taskName)
            assertNull(run.error)
            assertTrue(run.outputJson!!.contains("Geminids"))
        }

    @Test
    fun `a failed run is recorded with its error and the delivery results`() =
        runBlocking {
            val runner = runner(notifiers = listOf(FakeNotifier("discord"), FakeNotifier("telegram", failWith = "bad token")))
            val task =
                runner.createTask(
                    name = "Broken",
                    type = TaskType.DARK_WINDOW,
                    payload = DarkWindowRequest(10.3, 123.9, "not-a-date", "Asia/Manila"),
                    payloadSerializer = DarkWindowRequest.serializer(),
                    notify = NotifyPolicy.ON_FAILURE,
                )

            runner.runTask(task.id)

            val run = runs.findByTaskId(task.id, 10).single()
            assertEquals(TaskStatus.FAILED, run.status)
            assertTrue(run.error!!.contains("not-a-date"), run.error)
            assertNull(run.outputJson)
            assertEquals(
                listOf(NotificationDelivery("discord", true), NotificationDelivery("telegram", false, "bad token")),
                run.deliveries,
            )
        }

    @Test
    fun `scheduled runs are marked TICK and disabled runs are still recorded`() =
        runBlocking {
            val runner = runner()
            val daily = runner.meteorTask(frequency = TaskFrequency.DAILY)
            val disabled = runner.meteorTask(enabled = false)

            runner.runAllEnabled()
            runner.runTask(disabled.id)

            assertEquals(TaskRunTrigger.TICK, runs.findByTaskId(daily.id, 10).single().trigger)
            val disabledRun = runs.findByTaskId(disabled.id, 10).single()
            assertEquals(TaskStatus.FAILED, disabledRun.status)
            assertEquals("Task disabled", disabledRun.error)
        }

    @Test
    fun `history is most recent first and keeps every run`() =
        runBlocking {
            val runner = runner()
            val task = runner.meteorTask()

            val ids = (1..3).map { runner.runTask(task.id)!!.runId }

            assertEquals(ids.reversed(), runs.findByTaskId(task.id, 10).map { it.id })
            assertEquals(ids.reversed().take(2), runs.findByTaskId(task.id, 2).map { it.id })
        }

    @Test
    fun `a broken history store does not fail the run`() =
        runBlocking {
            val broken =
                object : TaskRunRepository {
                    override fun record(run: TaskRun): TaskRun = throw IllegalStateException("Firestore unavailable")

                    override fun findByTaskId(
                        taskId: String,
                        limit: Int,
                    ): List<TaskRun> = emptyList()
                }
            val runner = runner(runRepository = broken)
            val task = runner.meteorTask()

            val result = runner.runTask(task.id)

            assertNotNull(result)
            assertEquals(TaskStatus.SUCCESS, result.task.lastStatus)
            assertNull(result.runId)
        }
}
