package com.github.ioj0230.astro.core.meteor

import kotlinx.serialization.Serializable

@Serializable
data class MeteorShowerEvent(
    val name: String,
    val peakDateIso: String,
    // zhr: Zenithal Hourly Rate (approx. meteors per hour)
    val zhr: Int,
    val radiantConstellation: String,
    val bestViewStartIso: String,
    val bestViewEndIso: String,
    val radiantDirection: String,
    val notes: String? = null,
)

@Serializable
data class MeteorAlertRequest(
    val latitude: Double,
    val longitude: Double,
    val dateIso: String,
    val timeZoneId: String,
)

@Serializable
data class MeteorAlertResponse(
    val events: List<MeteorShowerEvent>,
    val summary: String,
)
