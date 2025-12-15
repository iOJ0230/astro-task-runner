package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.sky.SkySummaryService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class TaskRunResult(
    val task: Task,
    val outputJson: String? = null,
)

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

    /**
     * Generic task creation helper.
     *
     * This keeps TaskRunner as a "deep module":
     * routes supply a request payload, TaskRunner handles ID/time/serialization/storage.
     */
    fun <T> createTask(
        name: String,
        type: TaskType,
        payload: T,
        payloadSerializer: KSerializer<T>,
        frequency: TaskFrequency = TaskFrequency.MANUAL,
        preferredHourUtc: Int? = null,
        enabled: Boolean = true,
    ): Task {
        require(name.isNotBlank()) { "name must not be blank" }
        if (frequency == TaskFrequency.DAILY) {
            require(preferredHourUtc != null) { "preferredHourUtc is required when frequency is DAILY" }
            require(preferredHourUtc in 0..23) { "preferredHourUtc must be within 0..23" }
        }
        if (preferredHourUtc != null) {
            require(preferredHourUtc in 0..23) { "preferredHourUtc must be within 0..23" }
        }

        val nowIso = nowUtc().toString()

        val task =
            Task(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                payloadJson = json.encodeToString(payloadSerializer, payload),
                createdAtIso = nowIso,
                frequency = frequency,
                preferredHourUtc = preferredHourUtc,
                enabled = enabled,
            )

        return taskRepository.create(task)
    }

    /**
     * Backwards-compatible wrapper (your existing API can keep calling this).
     */
    fun createDarkWindowTask(
        name: String,
        request: DarkWindowRequest,
        frequency: TaskFrequency = TaskFrequency.MANUAL,
        preferredHourUtc: Int? = null,
    ): Task {
        return createTask(
            name = name,
            type = TaskType.DARK_WINDOW,
            payload = request,
            payloadSerializer = DarkWindowRequest.serializer(),
            frequency = frequency,
            preferredHourUtc = preferredHourUtc,
            enabled = true,
        )
    }

    fun runTask(taskId: String): TaskRunResult? {
        val existing = taskRepository.findById(taskId) ?: return null
        if (!existing.enabled) {
            val updated =
                existing.copy(
                    lastStatus = TaskStatus.FAILED,
                    lastError = "Task disabled",
                )
            return TaskRunResult(taskRepository.update(updated), outputJson = null)
        }

        val nowIso = nowUtc().toString()

        return when (existing.type) {
            TaskType.DARK_WINDOW -> runDarkWindowTask(existing, nowIso)
            TaskType.METEOR_ALERT -> runMeteorAlertTask(existing, nowIso)
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

    private fun runMeteorAlertTask(
        task: Task,
        nowIso: String,
    ): TaskRunResult {
        return try {
            val request =
                json.decodeFromString(
                    MeteorAlertRequest.serializer(),
                    task.payloadJson,
                )

            val response: MeteorAlertResponse =
                astroEventService.upcomingMeteorShowers(request)

            val outputJson =
                json.encodeToString(
                    MeteorAlertResponse.serializer(),
                    response,
                )

            val updated =
                task.copy(
                    lastRunAtIso = nowIso,
                    lastStatus = TaskStatus.SUCCESS,
                    lastError = null,
                )

            TaskRunResult(taskRepository.update(updated), outputJson)
        } catch (e: Exception) {
            val updated =
                task.copy(
                    lastRunAtIso = nowIso,
                    lastStatus = TaskStatus.FAILED,
                    lastError = e.message ?: "Meteor alert task failed",
                )

            TaskRunResult(taskRepository.update(updated), outputJson = null)
        }
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

        // If task already ran today, don't run again
        if (lastDate.isEqual(nowDate)) {
            return false
        }

        // If it's a new day, and it's reached preferred hour → run
        return nowHour >= preferredHour
    }
}
