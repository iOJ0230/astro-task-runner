package com.github.ioj0230.astro.api.task

import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.core.task.TaskType
import com.github.ioj0230.astro.module
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DarkWindowTaskRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should create + run dark-window task and succeeds`() =
        testApplication {
            application {
                module()
            }

            // Create dark-window task
            val createResponse =
                client.post("/api/tasks/dark-window") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Dark window Cebu",
                          "darkWindowRequest": {
                            "latitude": 10.3111,
                            "longitude": 123.8854,
                            "dateIso": "2025-12-14",
                            "timeZoneId": "Asia/Manila"
                          },
                          "frequency": "MANUAL",
                          "enabled": true
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, createResponse.status)

            val createdTask =
                json.decodeFromString<Task>(
                    createResponse.bodyAsText(),
                )

            assertEquals(TaskType.DARK_WINDOW, createdTask.type)
            assertNotNull(createdTask.id)

            // Run the task
            val runResponse =
                client.post("/api/tasks/${createdTask.id}/run")

            assertEquals(HttpStatusCode.OK, runResponse.status)

            val runResult =
                json.decodeFromString<TaskRunApiResponse>(
                    runResponse.bodyAsText(),
                )

            // Assertions on execution result
            assertEquals(TaskStatus.SUCCESS, runResult.task.lastStatus)
            assertNotNull(runResult.outputJson)

            // Loose assertion: output should mention the domain object
            assertTrue(
                runResult.outputJson.contains("window", ignoreCase = true) ||
                    runResult.outputJson.contains("dark", ignoreCase = true),
                "outputJson should contain dark window information",
            )
        }

    @Test
    fun `should run disabled dark-window task and fails`() =
        testApplication {
            application {
                module()
            }

            // Create disabled dark-window task
            val createResponse =
                client.post("/api/tasks/dark-window") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Disabled dark window task",
                          "darkWindowRequest": {
                            "latitude": 10.3111,
                            "longitude": 123.8854,
                            "dateIso": "2025-12-14",
                            "timeZoneId": "Asia/Manila"
                          },
                          "frequency": "MANUAL",
                          "enabled": false
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, createResponse.status)

            val createdTask =
                json.decodeFromString<Task>(
                    createResponse.bodyAsText(),
                )

            val runResponse =
                client.post("/api/tasks/${createdTask.id}/run")

            assertEquals(HttpStatusCode.OK, runResponse.status)

            val runResult =
                json.decodeFromString<TaskRunApiResponse>(
                    runResponse.bodyAsText(),
                )

            assertEquals(TaskStatus.FAILED, runResult.task.lastStatus)
            assertEquals("Task disabled", runResult.task.lastError)
            assertEquals(null, runResult.outputJson)
        }
}
