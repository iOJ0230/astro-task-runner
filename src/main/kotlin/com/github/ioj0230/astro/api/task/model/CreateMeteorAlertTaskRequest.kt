package com.github.ioj0230.astro.api.task.model

import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.task.TaskFrequency
import kotlinx.serialization.Serializable

@Serializable
data class CreateMeteorAlertTaskRequest(
    val name: String,
    val meteorAlertRequest: MeteorAlertRequest,
    val frequency: TaskFrequency = TaskFrequency.MANUAL,
    val preferredHourUtc: Int? = null,
    val enabled: Boolean = true,
)
