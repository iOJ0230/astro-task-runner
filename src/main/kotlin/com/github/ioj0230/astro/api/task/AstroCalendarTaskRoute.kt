package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.task.model.CreateAstroCalendarTaskRequest
import com.github.ioj0230.astro.core.calendar.AstroCalendarRequest
import com.github.ioj0230.astro.core.task.TaskType
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.astroCalendarTaskRoute(services: ServiceRegistry) {
    post("/api/tasks/astro-calendar") {
        val req = call.receive<CreateAstroCalendarTaskRequest>()

        val created =
            services.taskRunner.createTask(
                name = req.name,
                type = TaskType.ASTRO_CALENDAR,
                payload = req.astroCalendarRequest,
                payloadSerializer = AstroCalendarRequest.serializer(),
                frequency = req.frequency,
                preferredHourUtc = req.preferredHourUtc,
                enabled = req.enabled,
                notify = req.notify,
            )

        call.respond(created)
    }
}
