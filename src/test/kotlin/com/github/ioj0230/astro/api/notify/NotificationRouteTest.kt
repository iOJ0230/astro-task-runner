package com.github.ioj0230.astro.api.notify

import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.testModule
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sends a test message to every configured channel`() =
        testApplication {
            val discord = FakeNotifier("discord")
            application { testModule(listOf(discord)) }

            val response =
                client.post("/api/notifications") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<SendNotificationResponse>(response.bodyAsText())
            assertEquals(listOf(NotificationDelivery("discord", true)), body.deliveries)
            assertEquals("Test notification from astro-task-runner", discord.sent.single().title)
        }

    @Test
    fun `no configured channels is a 409, not a silent success`() =
        testApplication {
            application { testModule() }

            val response =
                client.post("/api/notifications") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("NO_NOTIFICATION_CHANNELS"))
        }
}
