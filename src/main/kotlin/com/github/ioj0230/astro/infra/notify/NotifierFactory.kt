package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notifier
import io.ktor.client.HttpClient

/**
 * Builds the enabled [Notifier]s from environment variables. A channel is
 * enabled when its variables are set and skipped when they are all unset,
 * so local dev and tests need no configuration at all.
 *
 * A channel that is *half* configured (e.g. a Telegram token but no chat
 * id) fails startup instead of being silently skipped — silently skipped
 * notifications are exactly the failure you don't notice until you miss a
 * meteor shower.
 *
 * | Channel  | Variables                                |
 * |----------|------------------------------------------|
 * | Discord  | `DISCORD_WEBHOOK_URL`                    |
 * | Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` |
 */
object NotifierFactory {
    private val TELEGRAM_VARS = listOf("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID")

    fun fromEnvironment(
        env: Map<String, String>,
        httpClient: HttpClient,
    ): List<Notifier> {
        fun value(name: String): String? = env[name]?.takeIf { it.isNotBlank() }

        val notifiers = mutableListOf<Notifier>()

        value("DISCORD_WEBHOOK_URL")?.let { notifiers += DiscordWebhookNotifier(httpClient, it) }

        if (requireAllOrNone("telegram", TELEGRAM_VARS, ::value)) {
            notifiers += TelegramNotifier(httpClient, value("TELEGRAM_BOT_TOKEN")!!, value("TELEGRAM_CHAT_ID")!!)
        }

        return notifiers
    }

    /** @return true if every var is set, false if none are; throws if only some are. */
    private fun requireAllOrNone(
        channel: String,
        vars: List<String>,
        value: (String) -> String?,
    ): Boolean {
        val missing = vars.filter { value(it) == null }
        require(missing.isEmpty() || missing.size == vars.size) {
            "Notification channel '$channel' is partially configured; missing: ${missing.joinToString()}"
        }
        return missing.isEmpty()
    }
}
