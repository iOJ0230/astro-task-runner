package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.task.model.CreateDarkWindowTaskRequest
import com.github.ioj0230.astro.core.darkwindow.DarkWindowRequest
import com.github.ioj0230.astro.core.task.TaskType
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.darkWindowTaskRoute(services: ServiceRegistry) {
    post("/api/tasks/dark-window") {
        val req = call.receive<CreateDarkWindowTaskRequest>()

        val created =
            services.taskRunner.createTask(
                name = req.name,
                type = TaskType.DARK_WINDOW,
                payload = req.darkWindowRequest,
                payloadSerializer = DarkWindowRequest.serializer(),
                frequency = req.frequency,
                preferredHourUtc = req.preferredHourUtc,
                enabled = req.enabled,
            )

        call.respond(created)
    }
}
