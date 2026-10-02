package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notifier
import io.ktor.client.HttpClient

/**
 * Builds the enabled [Notifier]s from environment variables. A channel is
 * enabled when its variables are set and skipped when they are unset,
 * so local dev and tests need no configuration at all.
 *
 * | Channel  | Variables             |
 * |----------|-----------------------|
 * | Discord  | `DISCORD_WEBHOOK_URL` |
 */
object NotifierFactory {
    fun fromEnvironment(
        env: Map<String, String>,
        httpClient: HttpClient,
    ): List<Notifier> {
        fun value(name: String): String? = env[name]?.takeIf { it.isNotBlank() }

        val notifiers = mutableListOf<Notifier>()

        value("DISCORD_WEBHOOK_URL")?.let { notifiers += DiscordWebhookNotifier(httpClient, it) }

        return notifiers
    }
}
