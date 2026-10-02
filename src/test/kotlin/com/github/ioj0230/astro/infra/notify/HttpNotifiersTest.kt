package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notification
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpNotifiersTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun clientResponding(status: HttpStatusCode = HttpStatusCode.NoContent) =
        HttpClient(
            MockEngine { request ->
                requests += request
                if (status.value < 400) respond("", status) else respondError(status, "nope")
            },
        )

    private suspend fun HttpRequestData.jsonBody() = Json.parseToJsonElement(String(body.toByteArray())).jsonObject

    @Test
    fun `discord posts content to the webhook url`() =
        runBlocking {
            val notifier = DiscordWebhookNotifier(clientResponding(), "https://discord.test/api/webhooks/1/abc")

            notifier.send(Notification("Orionids tonight", "Peak after midnight"))

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://discord.test/api/webhooks/1/abc", request.url.toString())
            assertEquals("Orionids tonight\n\nPeak after midnight", request.jsonBody()["content"]!!.jsonPrimitive.content)
        }

    @Test
    fun `discord truncates to its 2000 character limit`() =
        runBlocking {
            val notifier = DiscordWebhookNotifier(clientResponding(), "https://discord.test/hook")

            notifier.send(Notification("t", "x".repeat(5000)))

            val content = requests.single().jsonBody()["content"]!!.jsonPrimitive.content
            assertEquals(2000, content.length)
            assertTrue(content.endsWith("…"))
        }

    @Test
    fun `discord non-2xx responses throw with the status code`() =
        runBlocking {
            val notifier = DiscordWebhookNotifier(clientResponding(HttpStatusCode.NotFound), "https://discord.test/api/webhooks/1/abc")

            val error = assertFailsWith<NotifierException> { notifier.send(Notification("t", "b")) }

            assertTrue(error.message!!.startsWith("discord responded 404"), error.message)
            assertFalse(error.message!!.contains("webhooks/1/abc"))
        }

    @Test
    fun `discord network errors throw without echoing the webhook url`() =
        runBlocking {
            // Real connection errors often include the request URL, and a
            // Discord webhook URL is itself the credential.
            val failing =
                HttpClient(
                    MockEngine { request -> throw java.io.IOException("Connection refused: ${request.url}") },
                )
            val notifier = DiscordWebhookNotifier(failing, "https://discord.test/api/webhooks/1/abc")

            val error = assertFailsWith<NotifierException> { notifier.send(Notification("t", "b")) }

            assertEquals("discord request failed (IOException)", error.message)
        }

    @Test
    fun `telegram posts chat id and text to sendMessage`() =
        runBlocking {
            val notifier = TelegramNotifier(clientResponding(HttpStatusCode.OK), botToken = "123:secret", chatId = "42")

            notifier.send(Notification("New Moon", "Dark skies"))

            val request = requests.single()
            assertEquals("https://api.telegram.org/bot123:secret/sendMessage", request.url.toString())
            val body = request.jsonBody()
            assertEquals("42", body["chat_id"]!!.jsonPrimitive.content)
            assertEquals("New Moon\n\nDark skies", body["text"]!!.jsonPrimitive.content)
        }

    @Test
    fun `non-2xx responses throw without leaking the bot token`() =
        runBlocking {
            val notifier = TelegramNotifier(clientResponding(HttpStatusCode.Unauthorized), botToken = "123:secret", chatId = "42")

            val error = assertFailsWith<NotifierException> { notifier.send(Notification("t", "b")) }

            assertTrue(error.message!!.contains("401"))
            assertFalse(error.message!!.contains("secret"))
        }

    @Test
    fun `network errors throw without echoing the url`() =
        runBlocking {
            // Real connection errors often include the request URL; the
            // token must not end up in a delivery error or the logs.
            val failing =
                HttpClient(
                    MockEngine { request -> throw java.io.IOException("Connection refused: ${request.url}") },
                )
            val notifier = TelegramNotifier(failing, botToken = "123:secret", chatId = "42")

            val error = assertFailsWith<NotifierException> { notifier.send(Notification("t", "b")) }

            assertEquals("telegram request failed (IOException)", error.message)
        }
}
