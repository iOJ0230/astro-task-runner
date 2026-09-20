package com.github.ioj0230.astro.api.task.model

import com.github.ioj0230.astro.core.task.Task
import kotlinx.serialization.Serializable

@Serializable
data class TaskListResponse(
    val tasks: List<Task>,
)

@Serializable
data class TaskRunResponse(
    val task: Task,
    val outputJson: String? = null,
)

@Serializable
data class TaskTickResponse(
    val results: List<TaskRunResponse>,
)
