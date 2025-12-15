package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskFrequency
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun Route.taskRoute(services: ServiceRegistry) {
    @Serializable
    data class CreateDarkWindowTaskRequest(
        val name: String,
        val darkWindowRequest: DarkWindowRequest,
        val frequency: TaskFrequency = TaskFrequency.MANUAL,
        val preferredHourUtc: Int? = null,
    )

    @Serializable
    data class TaskListResponse(
        val tasks: List<Task>,
    )

    @Serializable
    data class TaskRunResponse(
        val task: Task,
        val outputJson: String? = null,
    )

    @Serializable
    data class TaskTickResponse(
        val results: List<TaskRunResponse>,
    )

    // List all tasks
    get("/api/tasks") {
        val tasks = services.taskRepository.findAll()
        call.respond(TaskListResponse(tasks))
    }

    // Get one task
    get("/api/tasks/{id}") {
        val id =
            call.parameters["id"] ?: return@get call.respondText(
                "Missing id", status = io.ktor.http.HttpStatusCode.BadRequest,
            )
        val task = services.taskRepository.findById(id)
        if (task == null) {
            call.respondText("Task not found", status = io.ktor.http.HttpStatusCode.NotFound)
        } else {
            call.respond(task)
        }
    }

    // Run a task once, immediately
    post("/api/tasks/{id}/run") {
        val id =
            call.parameters["id"] ?: return@post call.respondText(
                "Missing id", status = io.ktor.http.HttpStatusCode.BadRequest,
            )

        val result =
            services.taskRunner.runTask(id)
                ?: return@post call.respondText("Task not found", status = io.ktor.http.HttpStatusCode.NotFound)

        call.respond(TaskRunResponse(task = result.task, outputJson = result.outputJson))
    }

    // Run all enabled tasks (for Cloud Scheduler)
    post("/api/tasks/tick") {
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
