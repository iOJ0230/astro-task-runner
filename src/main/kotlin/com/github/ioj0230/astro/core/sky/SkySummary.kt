package com.github.ioj0230.astro.core.sky

import com.github.ioj0230.astro.core.darkwindow.DarkWindow
import com.github.ioj0230.astro.core.meteor.MeteorShowerEvent
import kotlinx.serialization.Serializable

@Serializable
data class SkySummaryRequest(
    val latitude: Double,
    val longitude: Double,
    val dateIso: String,
    val timeZoneId: String
)

@Serializable
data class SkySummaryResponse(
    val dateIso: String,
    val timeZoneId: String,
    val darkWindow: DarkWindow,
    val meteors: List<MeteorShowerEvent>,
    val overallSummary: String,
    val notes: String? = null
)