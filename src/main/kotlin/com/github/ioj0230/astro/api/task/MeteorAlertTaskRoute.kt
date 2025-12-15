package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.task.model.CreateMeteorAlertTaskRequest
import com.github.ioj0230.astro.core.meteor.MeteorAlertRequest
import com.github.ioj0230.astro.core.task.TaskType
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.meteorAlertTaskRoute(services: ServiceRegistry) {
    post("/api/tasks/meteor-alert") {
        val req = call.receive<CreateMeteorAlertTaskRequest>()

        val created =
            services.taskRunner.createTask(
                name = req.name,
                type = TaskType.METEOR_ALERT,
                payload = req.meteorAlertRequest,
                payloadSerializer = MeteorAlertRequest.serializer(),
                frequency = req.frequency,
                preferredHourUtc = req.preferredHourUtc,
                enabled = req.enabled,
            )

        call.respond(created)
    }
}
