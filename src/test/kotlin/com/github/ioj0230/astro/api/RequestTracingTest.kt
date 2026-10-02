package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.api.task.model.TaskRunListResponse
import com.github.ioj0230.astro.core.log.LogCapture
import com.github.ioj0230.astro.core.log.LogEvents
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskRunner
import com.github.ioj0230.astro.testModule
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A request id ties together the HTTP response, every log line, and the stored run. */
class RequestTracingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.createTask(): Task =
        json.decodeFromString(
            post("/api/tasks/astro-calendar") {
                contentType(ContentType.Application.Json)
                setBody("""{"name": "Sky", "astroCalendarRequest": {"timeZoneId": "Asia/Manila"}}""")
            }.bodyAsText(),
        )

    @Test
    fun `Cloud Run's trace id becomes the request id everywhere`() =
        testApplication {
            application { testModule() }
            val task = client.createTask()

            LogCapture(TaskRunner::class).use { capture ->
                val response =
                    client.post("/api/tasks/${task.id}/run") {
                        header("X-Cloud-Trace-Context", "105445aa7843bc8bf206b12000100000/1;o=1")
                    }

                assertEquals("105445aa7843bc8bf206b12000100000", response.headers["X-Request-Id"])

                val runLog = capture.withEvent(LogEvents.TASK_RUN_FINISHED).single()
                assertEquals("105445aa7843bc8bf206b12000100000", runLog.mdc["requestId"])
                assertEquals(task.id, runLog.fields["taskId"])

                val run = json.decodeFromString<TaskRunListResponse>(client.get("/api/tasks/${task.id}/runs").bodyAsText()).runs.single()
                assertEquals("105445aa7843bc8bf206b12000100000", run.requestId)
                assertEquals(run.id, runLog.fields["runId"])
            }
        }

    @Test
    fun `without a trace header a fresh id is generated per request`() =
        testApplication {
            application { testModule() }

            val first = client.get("/health").headers["X-Request-Id"]
            val second = client.get("/health").headers["X-Request-Id"]

            assertNotNull(first)
            assertTrue(first.matches(Regex("[0-9a-f]{32}")), first)
            assertNotEquals(first, second)
        }

    @Test
    fun `a malformed trace header is ignored rather than written into logs`() =
        testApplication {
            application { testModule() }

            val id =
                client.get("/health") {
                    header("X-Cloud-Trace-Context", "evil\" injected=\"yes/1")
                }.headers["X-Request-Id"]

            assertNotNull(id)
            assertTrue(id.matches(Regex("[0-9a-f]{32}")), id)
        }
}
