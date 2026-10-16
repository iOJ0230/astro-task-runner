package com.github.ioj0230.astro.core.notify

import kotlinx.coroutines.CancellationException

/**
 * Fans one [Notification] out to every configured [Notifier].
 *
 * Channels are isolated from each other: a broken Telegram token must not
 * stop the Discord or email copy from going out, and must not fail the
 * task that triggered the notification. Failures come back as
 * [NotificationDelivery] results instead of exceptions.
 */
class NotificationService(
    private val notifiers: List<Notifier>,
) {
    val channels: List<String> get() = notifiers.map { it.channel }

    suspend fun dispatch(notification: Notification): List<NotificationDelivery> =
        notifiers.map { notifier ->
            try {
                notifier.send(notification)
                NotificationDelivery(channel = notifier.channel, success = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NotificationDelivery(
                    channel = notifier.channel,
                    success = false,
                    error = e.message ?: e::class.simpleName,
                )
            }
        }
}
