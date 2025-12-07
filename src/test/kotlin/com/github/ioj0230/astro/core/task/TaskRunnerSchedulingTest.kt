package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.darkwindow.DarkWindow
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.sky.SkySummaryService
import com.github.ioj0230.astro.infra.task.InMemoryTaskRepository
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunnerSchedulingTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // Very small stub implementations so we don't depend on real dummy services:

    private val stubAstroMathService = object : AstroMathService {
        override fun computeDarkWindow(
            latitude: Double,
            longitude: Double,
            date: java.time.LocalDate,
            timeZoneId: String
        ): DarkWindow {
            return DarkWindow(
                startIso = "2025-08-12T20:00:00+08:00",
                endIso = "2025-08-13T03:00:00+08:00",
                description = "Stub dark window"
            )
        }

        override fun describeMoonPhase(
            dateTime: java.time.OffsetDateTime,
            latitude: Double,
            longitude: Double
        ): String = "Stub moon phase"

        override fun bestMilkyWayTimeHint(
            date: java.time.LocalDate,
            latitude: Double,
            longitude: Double
        ): String = "Stub milky way hint"
    }

    private val stubAstroEventService = object : AstroEventService {
        override fun upcomingMeteorShowers(request: MeteorAlertRequest): MeteorAlertResponse {
            return MeteorAlertResponse(events = emptyList(), summary = "Stub meteors")
        }
    }

    private val stubSkySummaryService = SkySummaryService(
        astroMathService = stubAstroMathService,
        astroEventService = stubAstroEventService
    )

    @Test
    fun `should not run MANUAL tasks`() {
        val repo = InMemoryTaskRepository()
        val runner = TaskRunner(
            taskRepository = repo,
            astroMathService = stubAstroMathService,
            astroEventService = stubAstroEventService,
            skySummaryService = stubSkySummaryService,
            json = json
        )

        val manualTask = runner.createDarkWindowTask(
            name = "Manual dark window",
            request = sampleDarkWindowRequest(),
            frequency = TaskFrequency.MANUAL,
            preferredHourUtc = null
        )

        val results = runner.runAllEnabled()

        // No tasks should be run
        assertTrue(results.isEmpty(), "MANUAL tasks should not be run by tick")

        val stored = repo.findById(manualTask.id)!!
        assertEquals(TaskStatus.NEVER_RUN, stored.lastStatus)
        assertEquals(null, stored.lastRunAtIso)
    }

    @Test
    fun `should run DAILY tasks once after preferred hour`() {
        val repo = InMemoryTaskRepository()
        val runner = TaskRunner(
            taskRepository = repo,
            astroMathService = stubAstroMathService,
            astroEventService = stubAstroEventService,
            skySummaryService = stubSkySummaryService,
            json = json
        )

        val dailyTask = runner.createDarkWindowTask(
            name = "Daily dark window",
            request = sampleDarkWindowRequest(),
            frequency = TaskFrequency.DAILY,
            preferredHourUtc = 0 // assume it's fine in tests
        )

        val resultsFirst = runner.runAllEnabled()
        assertEquals(1, resultsFirst.size, "First tick should run DAILY task once")

        val updated = repo.findById(dailyTask.id)!!
        assertEquals(TaskStatus.SUCCESS, updated.lastStatus)
        assertTrue(updated.lastRunAtIso != null)

        // Running tick again immediately should not run it again the same day
        val resultsSecond = runner.runAllEnabled()
        assertTrue(
            resultsSecond.isEmpty(),
            "Second tick on same day should not re-run DAILY task"
        )
    }

    private fun sampleDarkWindowRequest(): DarkWindowRequest =
        DarkWindowRequest(
            latitude = 10.3111,
            longitude = 123.8854,
            dateIso = "2025-08-12",
            timeZoneId = "Asia/Manila"
        )
}