package com.github.ioj0230.astro.core.task

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val name: String,
    val type: TaskType,
    // JSON for the underlying request
    val payloadJson: String,
    val enabled: Boolean = true,
    val createdAtIso: String,
    val lastRunAtIso: String? = null,
    val lastStatus: TaskStatus = TaskStatus.NEVER_RUN,
    val lastError: String? = null,
    val frequency: TaskFrequency = TaskFrequency.MANUAL,
    val preferredHourUtc: Int? = null,
    // When to send this task's result to the notification channels.
    // Defaults to NEVER so tasks stored before this field existed keep
    // behaving exactly as they did.
    val notify: NotifyPolicy = NotifyPolicy.NEVER,
)

@Serializable
enum class TaskStatus {
    NEVER_RUN,
    SUCCESS,
    FAILED,
}

@Serializable
enum class NotifyPolicy {
    NEVER,

    // Stay quiet while things work; speak up when a run fails.
    ON_FAILURE,

    // Every run: the result on success, the error on failure.
    ALWAYS,
    ;

    fun shouldNotify(succeeded: Boolean): Boolean =
        when (this) {
            NEVER -> false
            ON_FAILURE -> !succeeded
            ALWAYS -> true
        }
}

@Serializable
enum class TaskFrequency {
    // only /run endpoint, tick ignores
    MANUAL,

    // once per day is enough for now
    DAILY,

    // TODO: HOURLY, WEEKLY, CUSTOM_CRON, etc.
}
