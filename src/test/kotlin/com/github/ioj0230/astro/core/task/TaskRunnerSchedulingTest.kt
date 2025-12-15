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
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunnerSchedulingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    private val stubAstroMathService =
        object : AstroMathService {
            override fun computeDarkWindow(
                latitude: Double,
                longitude: Double,
                date: java.time.LocalDate,
                timeZoneId: String,
            ): DarkWindow =
                DarkWindow(
                    startIso = "2025-08-12T20:00:00+08:00",
                    endIso = "2025-08-13T03:00:00+08:00",
                    description = "Stub dark window",
                )

            override fun describeMoonPhase(
                dateTime: java.time.OffsetDateTime,
                latitude: Double,
                longitude: Double,
            ): String = "Stub moon phase"

            override fun bestMilkyWayTimeHint(
                date: java.time.LocalDate,
                latitude: Double,
                longitude: Double,
            ): String = "Stub milky way hint"
        }

    private val stubAstroEventService =
        object : AstroEventService {
            override fun upcomingMeteorShowers(request: MeteorAlertRequest): MeteorAlertResponse =
                MeteorAlertResponse(events = emptyList(), summary = "Stub meteors")
        }

    private val stubSkySummaryService =
        SkySummaryService(
            astroMathService = stubAstroMathService,
            astroEventService = stubAstroEventService,
        )

    @Test
    fun `should not run MANUAL tasks`() {
        val clock =
            Clock.fixed(
                Instant.parse("2025-08-12T10:00:00Z"),
                ZoneOffset.UTC,
            )

        val repo = InMemoryTaskRepository()
        val runner =
            TaskRunner(
                taskRepository = repo,
                astroMathService = stubAstroMathService,
                astroEventService = stubAstroEventService,
                skySummaryService = stubSkySummaryService,
                json = json,
                clock = clock,
            )

        val manualTask =
            runner.createDarkWindowTask(
                name = "Manual dark window",
                request = sampleDarkWindowRequest(),
                frequency = TaskFrequency.MANUAL,
                preferredHourUtc = null,
            )

        val results = runner.runAllEnabled()

        assertTrue(results.isEmpty(), "MANUAL tasks should not be run by tick")

        val stored = repo.findById(manualTask.id)!!
        assertEquals(TaskStatus.NEVER_RUN, stored.lastStatus)
        assertEquals(null, stored.lastRunAtIso)
    }

    @Test
    fun `should run DAILY tasks once per day after preferred hour`() {
        val clock =
            Clock.fixed(
                // 10:00 UTC
                Instant.parse("2025-08-12T10:00:00Z"),
                ZoneOffset.UTC,
            )

        val repo = InMemoryTaskRepository()
        val runner =
            TaskRunner(
                taskRepository = repo,
                astroMathService = stubAstroMathService,
                astroEventService = stubAstroEventService,
                skySummaryService = stubSkySummaryService,
                json = json,
                clock = clock,
            )

        val dailyTask =
            runner.createDarkWindowTask(
                name = "Daily dark window",
                request = sampleDarkWindowRequest(),
                frequency = TaskFrequency.DAILY,
                preferredHourUtc = 8,
            )

        val firstResults = runner.runAllEnabled()
        assertEquals(1, firstResults.size, "First tick should run DAILY task")

        val updated = repo.findById(dailyTask.id)!!
        assertEquals(TaskStatus.SUCCESS, updated.lastStatus)
        assertTrue(updated.lastRunAtIso != null)

        // Tick again at same clock time -> should not run again
        val secondResults = runner.runAllEnabled()
        assertTrue(
            secondResults.isEmpty(),
            "Second tick same day should not re-run DAILY task",
        )
    }

    private fun sampleDarkWindowRequest(): DarkWindowRequest =
        DarkWindowRequest(
            latitude = 10.3111,
            longitude = 123.8854,
            dateIso = "2025-08-12",
            timeZoneId = "Asia/Manila",
        )
}
