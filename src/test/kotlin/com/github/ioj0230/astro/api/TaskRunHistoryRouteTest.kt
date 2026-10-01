package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.api.task.model.TaskRunListResponse
import com.github.ioj0230.astro.api.task.model.TaskRunResponse
import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.testModule
import io.ktor.client.request.get
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

class TaskRunHistoryRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `lists a task's runs, most recent first`() =
        testApplication {
            application { testModule() }
            val task =
                json.decodeFromString<Task>(
                    client.post("/api/tasks/astro-calendar") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Sky", "astroCalendarRequest": {"timeZoneId": "Asia/Manila"}}""")
                    }.bodyAsText(),
                )
            val runIds =
                (1..2).map {
                    json.decodeFromString<TaskRunResponse>(client.post("/api/tasks/${task.id}/run").bodyAsText()).runId
                }

            val response = client.get("/api/tasks/${task.id}/runs")

            assertEquals(HttpStatusCode.OK, response.status)
            val runs = json.decodeFromString<TaskRunListResponse>(response.bodyAsText()).runs
            assertEquals(runIds.reversed(), runs.map { it.id })
            assertEquals(listOf(TaskStatus.SUCCESS, TaskStatus.SUCCESS), runs.map { it.status })
        }

    @Test
    fun `unknown task is a 404 and a bad limit is a 400`() =
        testApplication {
            application { testModule() }

            assertEquals(HttpStatusCode.NotFound, client.get("/api/tasks/nope/runs").status)

            val task =
                json.decodeFromString<Task>(
                    client.post("/api/tasks/astro-calendar") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name": "Sky", "astroCalendarRequest": {"timeZoneId": "UTC"}}""")
                    }.bodyAsText(),
                )
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/tasks/${task.id}/runs?limit=0").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/tasks/${task.id}/runs?limit=500").status)
        }
}
