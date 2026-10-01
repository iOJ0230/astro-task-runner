package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

/**
 * The header + env var pair gating endpoints that trigger side effects
 * (`POST /api/tasks/tick`, `POST /api/notifications`). Callers must send
 * `X-Tick-Secret: <TASK_RUNNER_TICK_SECRET>`. If the env var isn't set
 * (local dev, tests), the check is skipped — see CLAUDE.md "Resolved" #6
 * for why this is a minimal shared-secret check rather than full auth.
 */
private const val SHARED_SECRET_HEADER = "X-Tick-Secret"
private const val SHARED_SECRET_ENV_VAR = "TASK_RUNNER_TICK_SECRET"

/**
 * Responds 401 and returns false when the shared secret is configured and
 * the request doesn't carry it. Callers should `return` when this is false.
 */
suspend fun ApplicationCall.checkSharedSecret(): Boolean {
    val expectedSecret = System.getenv(SHARED_SECRET_ENV_VAR)
    if (expectedSecret.isNullOrBlank() || request.header(SHARED_SECRET_HEADER) == expectedSecret) {
        return true
    }
    respond(
        HttpStatusCode.Unauthorized,
        ApiErrorBody(
            ApiError(
                code = "UNAUTHORIZED",
                message = "Missing or invalid $SHARED_SECRET_HEADER header",
            ),
        ),
    )
    return false
}
