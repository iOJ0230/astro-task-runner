package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class TaskRunResult(
    val task: Task,
    val outputJson: String? = null,
)

interface TaskRepository {
    fun create(task: Task): Task

    fun findById(id: String): Task?

    fun findAll(): List<Task>

    fun update(task: Task): Task
}

class TaskRunner(
    private val taskRepository: TaskRepository,
    private val astroMathService: AstroMathService,
    private val astroEventService: AstroEventService,
    private val skySummaryService: SkySummaryService,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        },
    private val clock: Clock = Clock.systemUTC(),
) {
    private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(clock)

    fun createDarkWindowTask(
        name: String,
        request: DarkWindowRequest,
        frequency: TaskFrequency = TaskFrequency.MANUAL,
        preferredHourUtc: Int? = null,
    ): Task {
        val now = nowUtc().toString()
        val task =
            Task(
                id = UUID.randomUUID().toString(),
                name = name,
                type = "dark-window",
                payloadJson = json.encodeToString(DarkWindowRequest.serializer(), request),
                createdAtIso = now,
                frequency = frequency,
                preferredHourUtc = preferredHourUtc,
            )
        return taskRepository.create(task)
    }

    fun runTask(taskId: String): TaskRunResult? {
        val existing = taskRepository.findById(taskId) ?: return null
        if (!existing.enabled) {
            return TaskRunResult(
                existing.copy(
                    lastStatus = TaskStatus.FAILED,
                    lastError = "Task disabled",
                ),
            )
        }

        val nowIso = nowUtc().toString()

        return when (existing.type) {
            "dark-window" -> runDarkWindowTask(existing, nowIso)
            else -> {
                val updated =
                    existing.copy(
                        lastRunAtIso = nowIso,
                        lastStatus = TaskStatus.FAILED,
                        lastError = "Unknown task type: ${existing.type}",
                    )
                TaskRunResult(taskRepository.update(updated))
            }
        }
    }

    fun runAllEnabled(): List<TaskRunResult> {
        val all = taskRepository.findAll()
        val now = nowUtc()

        val due =
            all.filter { task ->
                task.enabled && isDue(task, now)
            }

        return due.map { task ->
            runTask(task.id) ?: TaskRunResult(
                task =
                    task.copy(
                        lastStatus = TaskStatus.FAILED,
                        lastError = "Task disappeared before tick run",
                    ),
                outputJson = null,
            )
        }
    }

    private fun runDarkWindowTask(
        task: Task,
        nowIso: String,
    ): TaskRunResult {
        val request =
            json.decodeFromString(
                DarkWindowRequest.serializer(),
                task.payloadJson,
            )

        val date = LocalDate.parse(request.dateIso)

        val window =
            astroMathService.computeDarkWindow(
                latitude = request.latitude,
                longitude = request.longitude,
                date = date,
                timeZoneId = request.timeZoneId,
            )

        val response =
            DarkWindowResponse(
                window = window,
                notes = "Task-run dark window result.",
            )

        val outputJson = json.encodeToString(DarkWindowResponse.serializer(), response)

        val updated =
            task.copy(
                lastRunAtIso = nowIso,
                lastStatus = TaskStatus.SUCCESS,
                lastError = null,
            )

        return TaskRunResult(taskRepository.update(updated), outputJson)
    }

    /**
     * Scheduling helpers
     */

    private fun isDue(
        task: Task,
        now: OffsetDateTime,
    ): Boolean {
        return when (task.frequency) {
            TaskFrequency.MANUAL -> false
            TaskFrequency.DAILY -> isDueDaily(task, now)
        }
    }

    private fun isDueDaily(
        task: Task,
        now: OffsetDateTime,
    ): Boolean {
        val preferredHour = task.preferredHourUtc ?: 0
        val nowDate = now.toLocalDate()
        val nowHour = now.hour

        // If task is never run, and it's past the preferred hour today → run it
        if (task.lastRunAtIso == null) {
            return nowHour >= preferredHour
        }

        val lastRun = OffsetDateTime.parse(task.lastRunAtIso)
        val lastDate = lastRun.toLocalDate()

        // If task already ran it today, don't run again
        if (lastDate.isEqual(nowDate)) {
            return false
        }

        // If it's a new day, and it's reached preferred hour → run
        return nowHour >= preferredHour
    }
}
