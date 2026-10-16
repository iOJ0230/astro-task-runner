package com.github.ioj0230.astro.core.notify

/** Records what it was asked to send; optionally fails every send. */
class FakeNotifier(
    override val channel: String = "fake",
    private val failWith: String? = null,
) : Notifier {
    val sent = mutableListOf<Notification>()

    override suspend fun send(notification: Notification) {
        failWith?.let { throw IllegalStateException(it) }
        sent += notification
    }
}
