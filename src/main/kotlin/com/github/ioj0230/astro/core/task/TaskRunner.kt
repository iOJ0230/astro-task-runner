package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.calendar.AstroCalendarRequest
import com.github.ioj0230.astro.core.calendar.AstroCalendarResponse
import com.github.ioj0230.astro.core.calendar.AstroCalendarService
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.math.AstroMathService
import com.github.ioj0230.astro.core.meteor.AstroEventService
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.notify.Notification
import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.notify.NotificationService
import com.github.ioj0230.astro.core.sky.SkySummaryService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class TaskRunResult(
    val task: Task,
    val outputJson: String? = null,
    // Empty unless the task has notify = true
    val deliveries: List<NotificationDelivery> = emptyList(),
)

class TaskRunner(
    private val taskRepository: TaskRepository,
    private val astroMathService: AstroMathService,
    private val astroEventService: AstroEventService,
    private val skySummaryService: SkySummaryService,
    private val astroCalendarService: AstroCalendarService,
    private val json: Json,
    private val notificationService: NotificationService = NotificationService(emptyList()),
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
        notify: Boolean = false,
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
                notify = notify,
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

    suspend fun runTask(taskId: String): TaskRunResult? {
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

        // One success/failure path for every task type: a throwing task is
        // recorded as FAILED on that task instead of escaping — which would
        // otherwise abort `tick` for every task after it.
        val output =
            try {
                execute(existing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return recordFailure(existing, nowIso, e)
            }

        val succeeded =
            existing.copy(
                lastRunAtIso = nowIso,
                lastStatus = TaskStatus.SUCCESS,
                lastError = null,
            )
        val stored = taskRepository.update(succeeded)
        val deliveries = notifyIfEnabled(stored, output.notification)
        return TaskRunResult(stored, output.outputJson, deliveries)
    }

    private suspend fun recordFailure(
        task: Task,
        nowIso: String,
        cause: Exception,
    ): TaskRunResult {
        val failed =
            task.copy(
                lastRunAtIso = nowIso,
                lastStatus = TaskStatus.FAILED,
                lastError = cause.message ?: "${task.type} task failed",
            )
        val stored = taskRepository.update(failed)
        val deliveries = notifyIfEnabled(stored, TaskNotifications.failure(stored))
        return TaskRunResult(stored, outputJson = null, deliveries = deliveries)
    }

    suspend fun runAllEnabled(): List<TaskRunResult> {
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

    private suspend fun notifyIfEnabled(
        task: Task,
        notification: Notification,
    ): List<NotificationDelivery> = if (task.notify) notificationService.dispatch(notification) else emptyList()

    private class TaskOutput(
        val outputJson: String,
        val notification: Notification,
    )

    private fun execute(task: Task): TaskOutput =
        when (task.type) {
            TaskType.DARK_WINDOW -> executeDarkWindow(task)
            TaskType.METEOR_ALERT -> executeMeteorAlert(task)
            TaskType.ASTRO_CALENDAR -> executeAstroCalendar(task)
        }

    private fun executeDarkWindow(task: Task): TaskOutput {
        val request = json.decodeFromString(DarkWindowRequest.serializer(), task.payloadJson)

        val window =
            astroMathService.computeDarkWindow(
                latitude = request.latitude,
                longitude = request.longitude,
                date = LocalDate.parse(request.dateIso),
                timeZoneId = request.timeZoneId,
            )

        val response =
            DarkWindowResponse(
                window = window,
                notes = "Task-run dark window result.",
            )

        return TaskOutput(
            outputJson = json.encodeToString(DarkWindowResponse.serializer(), response),
            notification = TaskNotifications.darkWindow(task, response),
        )
    }

    private fun executeMeteorAlert(task: Task): TaskOutput {
        val request = json.decodeFromString(MeteorAlertRequest.serializer(), task.payloadJson)
        val response: MeteorAlertResponse = astroEventService.upcomingMeteorShowers(request)

        return TaskOutput(
            outputJson = json.encodeToString(MeteorAlertResponse.serializer(), response),
            notification = TaskNotifications.meteorAlert(task, response),
        )
    }

    private fun executeAstroCalendar(task: Task): TaskOutput {
        val request = json.decodeFromString(AstroCalendarRequest.serializer(), task.payloadJson)
        val response = astroCalendarService.upcoming(request)

        return TaskOutput(
            outputJson = json.encodeToString(AstroCalendarResponse.serializer(), response),
            notification = TaskNotifications.astroCalendar(task, response),
        )
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
