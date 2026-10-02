package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.core.notify.FakeNotifier
import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.notify.Notifier
import com.github.ioj0230.astro.core.task.NotifyPolicy
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.core.task.TaskType
import com.github.ioj0230.astro.testModule
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstroCalendarTaskRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.createCalendarTask(notify: NotifyPolicy): Task {
        val response =
            post("/api/tasks/astro-calendar") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "name": "October sky",
                      "astroCalendarRequest": {
                        "timeZoneId": "Asia/Manila",
                        "startDateIso": "2026-10-20",
                        "days": 7
                      },
                      "notify": "$notify"
                    }
                    """.trimIndent(),
                )
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private fun ApplicationTestBuilder.appWith(notifiers: List<Notifier>) = application { testModule(notifiers) }

    @Test
    fun `running a notify task sends the calendar to every channel`() =
        testApplication {
            val discord = FakeNotifier("discord")
            val email = FakeNotifier("email")
            appWith(listOf(discord, email))

            val task = client.createCalendarTask(notify = NotifyPolicy.ALWAYS)
            assertEquals(TaskType.ASTRO_CALENDAR, task.type)
            assertEquals(NotifyPolicy.ALWAYS, task.notify)

            val run = json.decodeFromString<TaskRunResponse>(client.post("/api/tasks/${task.id}/run").bodyAsText())

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertEquals(
                listOf(NotificationDelivery("discord", true), NotificationDelivery("email", true)),
                run.deliveries,
            )
            val message = discord.sent.single()
            assertTrue(message.title.contains("Oct 20 – Oct 26"), message.title)
            assertTrue(message.body.contains("Oct 21 · Orionid meteor shower"), message.body)
            assertTrue(message.body.contains("Source: dummy"), message.body)
            assertEquals(message, email.sent.single())
        }

    @Test
    fun `a broken channel does not fail the task`() =
        testApplication {
            appWith(listOf(FakeNotifier("telegram", failWith = "chat not found")))

            val task = client.createCalendarTask(notify = NotifyPolicy.ALWAYS)
            val run = json.decodeFromString<TaskRunResponse>(client.post("/api/tasks/${task.id}/run").bodyAsText())

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertEquals(listOf(NotificationDelivery("telegram", false, "chat not found")), run.deliveries)
        }

    @Test
    fun `tasks without notify send nothing`() =
        testApplication {
            val discord = FakeNotifier("discord")
            appWith(listOf(discord))

            val task = client.createCalendarTask(notify = NotifyPolicy.NEVER)
            val run = json.decodeFromString<TaskRunResponse>(client.post("/api/tasks/${task.id}/run").bodyAsText())

            assertEquals(TaskStatus.SUCCESS, run.task.lastStatus)
            assertTrue(run.deliveries.isEmpty())
            assertTrue(discord.sent.isEmpty())
        }
}
