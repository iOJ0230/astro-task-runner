package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import com.github.ioj0230.astro.api.task.model.TaskListResponse
import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.api.task.model.TaskTickResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The header + env var pair used to gate `POST /api/tasks/tick`. Cloud
 * Scheduler (or any external caller) must send
 * `X-Tick-Secret: <TASK_RUNNER_TICK_SECRET>`. If the env var isn't set
 * (local dev, tests), the check is skipped — see docs/CONVENTIONS.md and
 * CLAUDE.md "Resolved" #6 for why this is a minimal shared-secret check
 * rather than full auth.
 */
private const val TICK_SECRET_HEADER = "X-Tick-Secret"
private const val TICK_SECRET_ENV_VAR = "TASK_RUNNER_TICK_SECRET"

fun Route.taskRoute(services: ServiceRegistry) {
    // List all tasks
    get("/api/tasks") {
        val tasks = services.taskRepository.findAll()
        call.respond(TaskListResponse(tasks))
    }

    // Get one task
    get("/api/tasks/{id}") {
        val id = call.parameters["id"]
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, missingIdError())
            return@get
        }

        val task = services.taskRepository.findById(id)
        if (task == null) {
            call.respond(HttpStatusCode.NotFound, taskNotFoundError(id))
        } else {
            call.respond(task)
        }
    }

    // Run a task once, immediately
    post("/api/tasks/{id}/run") {
        val id = call.parameters["id"]
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, missingIdError())
            return@post
        }

        val result = services.taskRunner.runTask(id)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, taskNotFoundError(id))
            return@post
        }

        call.respond(TaskRunResponse(task = result.task, outputJson = result.outputJson))
    }

    // Run all enabled + due tasks (for Cloud Scheduler)
    post("/api/tasks/tick") {
        val expectedSecret = System.getenv(TICK_SECRET_ENV_VAR)
        if (!expectedSecret.isNullOrBlank()) {
            val providedSecret = call.request.header(TICK_SECRET_HEADER)
            if (providedSecret != expectedSecret) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorBody(
                        ApiError(
                            code = "UNAUTHORIZED",
                            message = "Missing or invalid $TICK_SECRET_HEADER header",
                        ),
                    ),
                )
                return@post
            }
        }

        val runResults = services.taskRunner.runAllEnabled()

        val response =
            TaskTickResponse(
                results =
                    runResults.map { result ->
                        TaskRunResponse(
                            task = result.task,
                            outputJson = result.outputJson,
                        )
                    },
            )

        call.respond(response)
    }
}

private fun missingIdError() = ApiErrorBody(ApiError(code = "MISSING_ID", message = "id path parameter is required"))

private fun taskNotFoundError(id: String) = ApiErrorBody(ApiError(code = "TASK_NOT_FOUND", message = "No task with id $id"))
