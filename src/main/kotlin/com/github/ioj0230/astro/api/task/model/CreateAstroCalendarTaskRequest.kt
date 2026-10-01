package com.github.ioj0230.astro.api.task.model

import com.github.ioj0230.astro.core.calendar.AstroCalendarRequest
import com.github.ioj0230.astro.core.task.TaskFrequency
import kotlinx.serialization.Serializable

@Serializable
data class CreateAstroCalendarTaskRequest(
    val name: String,
    val astroCalendarRequest: AstroCalendarRequest,
    val frequency: TaskFrequency = TaskFrequency.MANUAL,
    val preferredHourUtc: Int? = null,
    val enabled: Boolean = true,
    val notify: Boolean = false,
)
