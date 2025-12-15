package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.core.task.Task
import kotlinx.serialization.Serializable

/**
 * Mirrors the API response for POST /api/tasks/{id}/run
 */
@Serializable
data class TaskRunApiResponse(
    val task: Task,
    val outputJson: String? = null,
)
