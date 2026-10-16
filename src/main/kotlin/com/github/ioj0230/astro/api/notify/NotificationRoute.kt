package com.github.ioj0230.astro.api.notify

import com.github.ioj0230.astro.ServiceRegistry
import com.github.ioj0230.astro.api.checkSharedSecret
import com.github.ioj0230.astro.api.model.ApiError
import com.github.ioj0230.astro.api.model.ApiErrorBody
import com.github.ioj0230.astro.core.notify.Notification
import com.github.ioj0230.astro.core.notify.NotificationDelivery
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class SendNotificationRequest(
    val title: String = "Test notification from astro-task-runner",
    val body: String = "If you can read this, this channel is set up correctly.",
)

@Serializable
data class SendNotificationResponse(
    val deliveries: List<NotificationDelivery>,
)

/**
 * `POST /api/notifications` sends one message to every configured
 * channel and reports per-channel success — the quickest way to check
 * Discord/Telegram/email setup without creating a task. Gated by the same
 * shared secret as `tick`, since it causes outbound messages.
 */
fun Route.notificationRoute(services: ServiceRegistry) {
    post("/api/notifications") {
        if (!call.checkSharedSecret()) return@post

        if (services.notificationService.channels.isEmpty()) {
            call.respond(
                HttpStatusCode.Conflict,
                ApiErrorBody(
                    ApiError(
                        code = "NO_NOTIFICATION_CHANNELS",
                        message = "No notification channels are configured; see docs/SETUP.md",
                    ),
                ),
            )
            return@post
        }

        val req = call.receive<SendNotificationRequest>()
        val deliveries = services.notificationService.dispatch(Notification(req.title, req.body))
        call.respond(SendNotificationResponse(deliveries))
    }
}
