package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.task.NotifyPolicy
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.testModule
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Notifications through the real routes + wiring, with fake channels. */
class TaskNotificationRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.createAndRunMeteorTask(notify: NotifyPolicy): TaskRunResponse {
        val created =
            post("/api/tasks/meteor-alert") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "name": "Geminids watch",
                      "meteorAlertRequest": {
                        "latitude": 10.3111, "longitude": 123.8854,
                        "dateIso": "2026-12-10", "timeZoneId": "Asia/Manila"
                      },
                      "notify": "$notify"
                    }
                    """.trimIndent(),
                )
            }
        val task = json.decodeFromString<Task>(created.bodyAsText())
        assertEquals(notify, task.notify)
        return json.decodeFromString(post("/api/tasks/${task.id}/run").bodyAsText())
    }

    @Test
    fun `ALWAYS sends the result to every channel and reports each delivery`() =
        testApplication {
            val discord = FakeNotifier("discord")
            val email = FakeNotifier("email")
            application { testModule(listOf(discord, email)) }

            val run = client.createAndRunMeteorTask(NotifyPolicy.ALWAYS)

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertEquals(listOf(NotificationDelivery("discord", true), NotificationDelivery("email", true)), run.deliveries)
            assertTrue(discord.sent.single().body.contains("Geminids"), discord.sent.single().body)
            assertEquals(discord.sent, email.sent)
        }

    @Test
    fun `a broken channel is reported but does not fail the task`() =
        testApplication {
            val discord = FakeNotifier("discord")
            application { testModule(listOf(FakeNotifier("telegram", failWith = "chat not found"), discord)) }

            val run = client.createAndRunMeteorTask(NotifyPolicy.ALWAYS)

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertEquals(
                listOf(NotificationDelivery("telegram", false, "chat not found"), NotificationDelivery("discord", true)),
                run.deliveries,
            )
            assertEquals(1, discord.sent.size)
        }

    @Test
    fun `ON_FAILURE stays quiet when the run succeeds`() =
        testApplication {
            val discord = FakeNotifier("discord")
            application { testModule(listOf(discord)) }

            val run = client.createAndRunMeteorTask(NotifyPolicy.ON_FAILURE)

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertTrue(run.deliveries.isEmpty())
            assertTrue(discord.sent.isEmpty())
        }
}
