package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import com.github.ioj0230.astro.api.task.model.TaskListResponse
import com.github.ioj0230.astro.api.task.model.TaskRunListResponse
import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.api.task.model.TaskTickResponse
import com.github.ioj0230.astro.core.task.TaskRunResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

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

    // Run history for one task, most recent first
    get("/api/tasks/{id}/runs") {
        val id = call.parameters["id"]
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, missingIdError())
            return@get
        }
        if (services.taskRepository.findById(id) == null) {
            call.respond(HttpStatusCode.NotFound, taskNotFoundError(id))
            return@get
        }
        val limit =
            call.request.queryParameters["limit"]?.let {
                requireNotNull(it.toIntOrNull()?.takeIf { n -> n in 1..MAX_RUNS_PAGE }) {
                    "limit must be a number within 1..$MAX_RUNS_PAGE"
                }
            } ?: DEFAULT_RUNS_PAGE

        call.respond(TaskRunListResponse(services.taskRunRepository.findByTaskId(id, limit)))
    }

    // Run a task once, immediately
    post("/api/tasks/{id}/run") {
        val id = call.parameters["id"]
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, missingIdError())
            return@post
        }

        val result = services.taskRunner.runTask(id, requestId = call.callId)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, taskNotFoundError(id))
            return@post
        }

        call.respond(result.toResponse())
    }

    // Run all enabled + due tasks (for Cloud Scheduler)
    post("/api/tasks/tick") {
        if (!call.checkSharedSecret()) return@post

        val runResults = services.taskRunner.runAllEnabled(requestId = call.callId)

        val response =
            TaskTickResponse(
                results =
                    runResults.map { it.toResponse() },
            )

        call.respond(response)
    }
}

private fun missingIdError() = ApiErrorBody(ApiError(code = "MISSING_ID", message = "id path parameter is required"))

private fun taskNotFoundError(id: String) = ApiErrorBody(ApiError(code = "TASK_NOT_FOUND", message = "No task with id $id"))

private const val DEFAULT_RUNS_PAGE = 20
private const val MAX_RUNS_PAGE = 100

private fun TaskRunResult.toResponse() = TaskRunResponse(task = task, outputJson = outputJson, deliveries = deliveries, runId = runId)
