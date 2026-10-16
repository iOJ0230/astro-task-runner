package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import com.github.ioj0230.astro.api.task.model.TaskListResponse
import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.api.task.model.TaskTickResponse
import com.github.ioj0230.astro.core.task.TaskRunResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
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

        call.logFailedDeliveries(result)
        call.respond(result.toResponse())
    }

    // Run all enabled + due tasks (for Cloud Scheduler)
    post("/api/tasks/tick") {
        if (!call.checkSharedSecret()) return@post

        val runResults = services.taskRunner.runAllEnabled()

        val response =
            TaskTickResponse(
                results =
                    runResults.map { result ->
                        call.logFailedDeliveries(result)
                        result.toResponse()
                    },
            )

        call.respond(response)
    }
}

private fun missingIdError() = ApiErrorBody(ApiError(code = "MISSING_ID", message = "id path parameter is required"))

private fun taskNotFoundError(id: String) = ApiErrorBody(ApiError(code = "TASK_NOT_FOUND", message = "No task with id $id"))

private fun TaskRunResult.toResponse() = TaskRunResponse(task = task, outputJson = outputJson, deliveries = deliveries)

// Scheduled ticks have no one reading the response, so a channel that
// stops working (revoked webhook, expired app password) must show up in
// the Cloud Run logs instead.
private fun ApplicationCall.logFailedDeliveries(result: TaskRunResult) {
    result.deliveries.filterNot { it.success }.forEach {
        application.log.warn("Notification via ${it.channel} failed for task ${result.task.id}: ${it.error}")
    }
}
