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
 * | Channel  | Variables                                                                 |
 * |----------|---------------------------------------------------------------------------|
 * | Discord  | `DISCORD_WEBHOOK_URL`                                                     |
 * | Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`                                  |
 * | Email    | `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `NOTIFY_EMAIL_TO`, optional `SMTP_PORT` (587), `NOTIFY_EMAIL_FROM` (= username), `SMTP_STARTTLS` (true) |
 *
 * `SMTP_STARTTLS=false` exists only for a local mail catcher such as
 * Mailpit, and is refused for any non-local host: without TLS the SMTP
 * password crosses the network in plain text.
 */
object NotifierFactory {
    private val TELEGRAM_VARS = listOf("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID")
    private val SMTP_REQUIRED_VARS = listOf("SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD", "NOTIFY_EMAIL_TO")
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "host.docker.internal")

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

        if (requireAllOrNone("email", SMTP_REQUIRED_VARS, ::value)) {
            val port =
                value("SMTP_PORT")?.let {
                    requireNotNull(it.toIntOrNull()) { "SMTP_PORT must be a number, got '$it'" }
                } ?: 587
            val startTls =
                when (val raw = value("SMTP_STARTTLS")?.lowercase()) {
                    null, "true" -> true
                    "false" -> false
                    else -> throw IllegalArgumentException("SMTP_STARTTLS must be true or false, got '$raw'")
                }
            require(startTls || value("SMTP_HOST")!!.lowercase() in LOCAL_HOSTS) {
                "SMTP_STARTTLS=false is only allowed for a local mail catcher (localhost); " +
                    "it would send the SMTP password unencrypted to ${value("SMTP_HOST")}"
            }
            notifiers +=
                EmailNotifier(
                    SmtpConfig(
                        host = value("SMTP_HOST")!!,
                        port = port,
                        username = value("SMTP_USERNAME")!!,
                        password = value("SMTP_PASSWORD")!!,
                        from = value("NOTIFY_EMAIL_FROM") ?: value("SMTP_USERNAME")!!,
                        to = value("NOTIFY_EMAIL_TO")!!,
                        startTls = startTls,
                    ),
                )
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
