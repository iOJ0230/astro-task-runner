package com.github.ioj0230.astro.api.task.model

import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskRun
import kotlinx.serialization.Serializable

@Serializable
data class TaskListResponse(
    val tasks: List<Task>,
)

@Serializable
data class TaskRunResponse(
    val task: Task,
    val outputJson: String? = null,
    // One entry per notification channel; empty unless task.notify is true
    val deliveries: List<NotificationDelivery> = emptyList(),
    // Look it up later via GET /api/tasks/{id}/runs
    val runId: String? = null,
)

@Serializable
data class TaskRunListResponse(
    val runs: List<TaskRun>,
)

@Serializable
data class TaskTickResponse(
    val results: List<TaskRunResponse>,
)
