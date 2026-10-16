package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notification
import com.github.ioj0230.astro.core.notify.Notifier
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Discord rejects messages over 2000 characters. */
private const val DISCORD_MAX_CHARS = 2000

/** How much of an error response body to keep in a delivery error. */
private const val ERROR_BODY_MAX_CHARS = 200

/**
 * Posts to a Discord channel webhook (Server Settings → Integrations →
 * Webhooks). No bot or OAuth needed — the URL itself is the credential,
 * so it must never appear in logs or error messages.
 */
class DiscordWebhookNotifier(
    private val httpClient: HttpClient,
    private val webhookUrl: String,
) : Notifier {
    override val channel = "discord"

    override suspend fun send(notification: Notification) {
        val payload =
            buildJsonObject {
                put("content", truncate(renderPlainText(notification), DISCORD_MAX_CHARS))
            }
        postJsonOrThrow(httpClient, channel, webhookUrl, payload.toString())
    }
}

class NotifierException(message: String) : RuntimeException(message)

internal fun renderPlainText(notification: Notification): String = "${notification.title}\n\n${notification.body}"

internal fun truncate(
    text: String,
    maxChars: Int,
): String = if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"

private suspend fun postJsonOrThrow(
    httpClient: HttpClient,
    channel: String,
    url: String,
    body: String,
) {
    val response: HttpResponse =
        try {
            httpClient.post(url) { setBody(TextContent(body, ContentType.Application.Json)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately drop e.message: connection errors can echo the
            // request URL, which contains the webhook URL / bot token.
            throw NotifierException("$channel request failed (${e::class.simpleName})")
        }

    if (!response.status.isSuccess()) {
        val detail = runCatching { response.bodyAsText() }.getOrDefault("").take(ERROR_BODY_MAX_CHARS)
        throw NotifierException("$channel responded ${response.status.value}: $detail")
    }
}
