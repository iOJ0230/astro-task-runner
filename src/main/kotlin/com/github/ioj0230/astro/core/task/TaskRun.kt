package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.notify.NotificationDelivery
import kotlinx.serialization.Serializable

@Serializable
enum class TaskRunTrigger {
    // POST /api/tasks/{id}/run
    MANUAL,

    // POST /api/tasks/tick (Cloud Scheduler)
    TICK,
}

/**
 * One execution of a [Task], successful or not. [Task] itself only keeps
 * the *latest* status/error (each run overwrites it); this is the
 * history, so "what happened at 08:00 last Tuesday, and did the Telegram
 * message go out?" has an answer.
 */
@Serializable
data class TaskRun(
    val id: String,
    val taskId: String,
    val taskName: String,
    val taskType: TaskType,
    val trigger: TaskRunTrigger,
    val startedAtIso: String,
    val finishedAtIso: String,
    // SUCCESS or FAILED
    val status: TaskStatus,
    val error: String? = null,
    val outputJson: String? = null,
    // One entry per notification channel; empty when nothing was sent
    val deliveries: List<NotificationDelivery> = emptyList(),
)

interface TaskRunRepository {
    fun record(run: TaskRun): TaskRun

    /** Most recent first. */
    fun findByTaskId(
        taskId: String,
        limit: Int,
    ): List<TaskRun>
}
