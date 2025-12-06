package com.github.ioj0230.astro.core.darkwindow

import kotlinx.serialization.Serializable

@Serializable
data class DarkWindowRequest(
    val latitude: Double,
    val longitude: Double,
    val dateIso: String,
    val timeZoneId: String
)

@Serializable
data class DarkWindow(
    val startIso: String,
    val endIso: String,
    val description: String
)

@Serializable
data class DarkWindowResponse(
    val window: DarkWindow,
    val notes: String? = null
)