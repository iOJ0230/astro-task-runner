package com.github.ioj0230.astro.core.notify

import kotlinx.serialization.Serializable

/**
 * A channel-agnostic message. Each [Notifier] decides how to render it
 * (Discord/Telegram get "title\n\nbody", email uses title as the subject).
 */
@Serializable
data class Notification(
    val title: String,
    val body: String,
)

/** Outcome of sending one [Notification] to one channel. */
@Serializable
data class NotificationDelivery(
    val channel: String,
    val success: Boolean,
    val error: String? = null,
)

/**
 * One delivery channel (Discord, Telegram, email, ...). Implementations
 * live in `infra/notify/` and should throw on failure — [NotificationService]
 * is what turns failures into [NotificationDelivery] results.
 */
interface Notifier {
    /** Stable lowercase id, e.g. "discord". Shown in delivery results. */
    val channel: String

    suspend fun send(notification: Notification)
}
