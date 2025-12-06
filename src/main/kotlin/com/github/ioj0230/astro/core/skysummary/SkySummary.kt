package com.github.ioj0230.astro.core.skysummary

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
    val locationDescription: String,
    val darkWindowSummary: String,
    val moonPhaseDescription: String,
    val milkyWayVisibility: String,
    val recommendedTimeRange: String
)