package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.calendar.AstroCalendarRequest
import com.github.ioj0230.astro.core.calendar.AstroCalendarService
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.infra.calendar.DummyAstroCalendarProvider
import com.github.ioj0230.astro.infra.math.DummyAstroMathService
import com.github.ioj0230.astro.infra.meteor.DummyAstroEventProvider
import com.github.ioj0230.astro.infra.task.InMemoryTaskRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunnerFailureTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)
    private val notifier = FakeNotifier()
    private val repo = InMemoryTaskRepository()
    private val runner =
        TaskRunner(
            taskRepository = repo,
            astroMathService = DummyAstroMathService(),
            astroEventService = DummyAstroEventProvider(),
            skySummaryService = SkySummaryService(DummyAstroMathService(), DummyAstroEventProvider()),
            astroCalendarService = AstroCalendarService(listOf(DummyAstroCalendarProvider()), clock),
            json = Json,
            notificationService = NotificationService(listOf(notifier)),
            clock = clock,
        )

    @Test
    fun `one throwing task is recorded as FAILED and does not abort the rest of the tick`() =
        runBlocking {
            // Bad date -> LocalDate.parse throws inside the dark-window run.
            // Before the unified run path, this escaped runAllEnabled and
            // the calendar task below never ran.
            val broken =
                runner.createTask(
                    name = "Broken dark window",
                    type = TaskType.DARK_WINDOW,
                    payload = DarkWindowRequest(10.3, 123.9, "not-a-date", "Asia/Manila"),
                    payloadSerializer = DarkWindowRequest.serializer(),
                    frequency = TaskFrequency.DAILY,
                    preferredHourUtc = 0,
                    notify = true,
                )
            val healthy =
                runner.createTask(
                    name = "Calendar",
                    type = TaskType.ASTRO_CALENDAR,
                    payload = AstroCalendarRequest(timeZoneId = "Asia/Manila", days = 3),
                    payloadSerializer = AstroCalendarRequest.serializer(),
                    frequency = TaskFrequency.DAILY,
                    preferredHourUtc = 0,
                    notify = true,
                )

            val results = runner.runAllEnabled().associateBy { it.task.id }

            assertEquals(TaskStatus.FAILED, results.getValue(broken.id).task.lastStatus)
            assertEquals(TaskStatus.SUCCESS, results.getValue(healthy.id).task.lastStatus)
            assertEquals(TaskStatus.FAILED, repo.findById(broken.id)!!.lastStatus)

            val titles = notifier.sent.map { it.title }
            assertTrue(titles.any { it.startsWith("⚠️ Task failed — Broken dark window") }, titles.toString())
            assertTrue(titles.any { it.startsWith("🔭 Sky calendar Oct 9 – Oct 11") }, titles.toString())
        }
}
