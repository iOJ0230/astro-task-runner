package com.github.ioj0230.astro.core.task

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,                 // UUID as string
    val name: String,               // "Dark window for Cebu"
    val type: String,               // "dark-window", "meteor-alert", "sky-summary"
    val payloadJson: String,        // JSON for the underlying request
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
    FAILED
}

@Serializable
enum class TaskFrequency {
    MANUAL,        // only /run endpoint, tick ignores
    DAILY          // once per day is enough for now
    // Later: HOURLY, WEEKLY, CUSTOM_CRON, etc.
}