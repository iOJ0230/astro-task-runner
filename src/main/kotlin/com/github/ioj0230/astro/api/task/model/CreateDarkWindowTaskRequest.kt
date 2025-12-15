package com.github.ioj0230.astro.api.task.model

import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.task.TaskFrequency
import kotlinx.serialization.Serializable

@Serializable
data class CreateDarkWindowTaskRequest(
    val name: String,
    val darkWindowRequest: DarkWindowRequest,
    val frequency: TaskFrequency = TaskFrequency.MANUAL,
    val preferredHourUtc: Int? = null,
    val enabled: Boolean = true,
)
