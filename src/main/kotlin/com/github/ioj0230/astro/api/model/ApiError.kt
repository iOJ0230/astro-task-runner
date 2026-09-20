package com.github.ioj0230.astro.api.model

import kotlinx.serialization.Serializable

/**
 * Consistent JSON error envelope for the whole API, installed centrally
 * via StatusPages in [com.github.ioj0230.astro.module]. See
 * docs/CONVENTIONS.md ("Error handling") for what this replaces.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorBody(
    val error: ApiError,
)
