package com.github.ioj0230.astro.core.meteors

import kotlinx.serialization.Serializable

@Serializable
data class MeteorShowerEvent(
    val name: String,
    val peakDateIso: String,
    val zhr: Int, // Zenithal Hourly Rate (approx. meteors per hour)
    val radiantConstellation: String,
    val bestViewStartIso: String,
    val bestViewEndIso: String,
    val radiantDirection: String,
    val notes: String? = null
)

@Serializable
data class MeteorAlertRequest(
    val latitude: Double,
    val longitude: Double,
    val dateIso: String,
    val timeZoneId: String
)

@Serializable
data class MeteorAlertResponse(
    val events: List<MeteorShowerEvent>,
    val summary: String
)