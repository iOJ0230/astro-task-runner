package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.task.Task
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.taskRoute(services: ServiceRegistry) {

    // --- API models just for creation / running ---

    @Serializable
    data class CreateDarkWindowTaskRequest(
        val name: String,
        val darkWindowRequest: DarkWindowRequest
    )

    @Serializable
    data class TaskListResponse(
        val tasks: List<Task>
    )

    @Serializable
    data class TaskRunResponse(
        val task: Task,
        val outputJson: String? = null
    )

    // --- Endpoints ---

    // Create a dark-window task
    post("/api/tasks/dark-window") {
        val request = call.receive<CreateDarkWindowTaskRequest>()
        val task = services.taskRunner.createDarkWindowTask(
            name = request.name,
            request = request.darkWindowRequest
        )
        call.respond(task)
    }

    // List all tasks
    get("/api/tasks") {
        val tasks = services.taskRepository.findAll()
        call.respond(TaskListResponse(tasks))
    }

    // Get one task
    get("/api/tasks/{id}") {
        val id = call.parameters["id"] ?: return@get call.respondText(
            "Missing id", status = io.ktor.http.HttpStatusCode.BadRequest
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
        val id = call.parameters["id"] ?: return@post call.respondText(
            "Missing id", status = io.ktor.http.HttpStatusCode.BadRequest
        )

        val result = services.taskRunner.runTask(id)
            ?: return@post call.respondText("Task not found", status = io.ktor.http.HttpStatusCode.NotFound)

        call.respond(TaskRunResponse(task = result.task, outputJson = result.outputJson))
    }

    // Later: /api/tasks/tick for scheduler
}