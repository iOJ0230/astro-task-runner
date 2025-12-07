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
    val lastError: String? = null
)

@Serializable
enum class TaskStatus {
    NEVER_RUN,
    SUCCESS,
    FAILED
}