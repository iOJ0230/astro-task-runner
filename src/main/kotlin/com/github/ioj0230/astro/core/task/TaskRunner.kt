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
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class TaskRunResult(
    val task: Task,
    val outputJson: String? = null,
    // One entry per channel; empty when the task's NotifyPolicy said not to send
    val deliveries: List<NotificationDelivery> = emptyList(),
    // Id of the stored TaskRun; null only if recording the history failed
    val runId: String? = null,
)

class TaskRunner(
    private val taskRepository: TaskRepository,
    private val taskRunRepository: TaskRunRepository,
    private val astroMathService: AstroMathService,
    private val astroEventService: AstroEventService,
    private val skySummaryService: SkySummaryService,
    private val astroCalendarService: AstroCalendarService,
    private val json: Json,
    private val notificationService: NotificationService = NotificationService(emptyList()),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(TaskRunner::class.java)

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
        notify: NotifyPolicy = NotifyPolicy.NEVER,
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

    suspend fun runTask(
        taskId: String,
        trigger: TaskRunTrigger = TaskRunTrigger.MANUAL,
    ): TaskRunResult? {
        val existing = taskRepository.findById(taskId) ?: return null
        val startedAt = nowUtc()

        val result =
            if (existing.enabled) {
                executeAndStore(existing, startedAt.toString())
            } else {
                val updated =
                    existing.copy(
                        lastStatus = TaskStatus.FAILED,
                        lastError = "Task disabled",
                    )
                TaskRunResult(taskRepository.update(updated), outputJson = null)
            }

        return result.copy(runId = recordRun(result, trigger, startedAt))
    }

    /**
     * Appends this run to the history. Never fails the run: if Firestore
     * rejects the write, the task's own status is already stored, so log
     * and carry on rather than turning a successful run into an error.
     */
    private fun recordRun(
        result: TaskRunResult,
        trigger: TaskRunTrigger,
        startedAt: OffsetDateTime,
    ): String? {
        val run =
            TaskRun(
                id = UUID.randomUUID().toString(),
                taskId = result.task.id,
                taskName = result.task.name,
                taskType = result.task.type,
                trigger = trigger,
                startedAtIso = startedAt.toString(),
                finishedAtIso = nowUtc().toString(),
                status = result.task.lastStatus,
                error = result.task.lastError,
                outputJson = result.outputJson,
                deliveries = result.deliveries,
            )
        return try {
            taskRunRepository.record(run).id
        } catch (e: Exception) {
            log.error("Could not record run history for task ${run.taskId}", e)
            null
        }
    }

    private suspend fun executeAndStore(
        existing: Task,
        nowIso: String,
    ): TaskRunResult {
        // One success/failure path for every task type: a throwing task is
        // recorded as FAILED on that task instead of escaping — which would
        // otherwise abort `tick` for every task after it.
        val output =
            try {
                execute(existing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed =
                    existing.copy(
                        lastRunAtIso = nowIso,
                        lastStatus = TaskStatus.FAILED,
                        lastError = e.message ?: "${existing.type} task failed",
                    )
                val stored = taskRepository.update(failed)
                val deliveries = notifyIfWanted(stored, succeeded = false, TaskNotifications.failure(stored))
                return TaskRunResult(stored, outputJson = null, deliveries = deliveries)
            }

        val succeeded =
            existing.copy(
                lastRunAtIso = nowIso,
                lastStatus = TaskStatus.SUCCESS,
                lastError = null,
            )
        val stored = taskRepository.update(succeeded)
        val deliveries = notifyIfWanted(stored, succeeded = true, output.notification)
        return TaskRunResult(stored, output.outputJson, deliveries)
    }

    private suspend fun notifyIfWanted(
        task: Task,
        succeeded: Boolean,
        notification: Notification,
    ): List<NotificationDelivery> = if (task.notify.shouldNotify(succeeded)) notificationService.dispatch(notification) else emptyList()

    private class TaskOutput(
        val outputJson: String,
        val notification: Notification,
    )

    suspend fun runAllEnabled(): List<TaskRunResult> {
        val all = taskRepository.findAll()
        val now = nowUtc()

        val due =
            all.filter { task ->
                task.enabled && isDue(task, now)
            }

        return due.map { task ->
            runTask(task.id, TaskRunTrigger.TICK) ?: TaskRunResult(
                task =
                    task.copy(
                        lastStatus = TaskStatus.FAILED,
                        lastError = "Task disappeared before tick run",
                    ),
                outputJson = null,
            )
        }
    }

    /** Runs the task's work and returns its output. Throws on failure. */
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
