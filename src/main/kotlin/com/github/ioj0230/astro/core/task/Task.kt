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
)

@Serializable
enum class TaskStatus {
    NEVER_RUN,
    SUCCESS,
    FAILED,
}

@Serializable
enum class TaskFrequency {
    // only /run endpoint, tick ignores
    MANUAL,

    // once per day is enough for now
    DAILY,

    // TODO: HOURLY, WEEKLY, CUSTOM_CRON, etc.
}
