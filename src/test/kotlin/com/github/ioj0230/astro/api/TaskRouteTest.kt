package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TaskRunResponse(
        val task: Task,
        val outputJson: String? = null,
    )

    @Test
    fun `should create dark-window task and return Task with id`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/tasks/dark-window") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Test dark-window task",
                          "darkWindowRequest": {
                            "latitude": 10.3111,
                            "longitude": 123.8854,
                            "dateIso": "2025-08-12",
                            "timeZoneId": "Asia/Manila"
                          },
                          "frequency": "MANUAL"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.bodyAsText()
            val task = json.decodeFromString<Task>(body)

            assertNotNull(task.id, "Task id should not be null")
            assertEquals("dark-window", task.type)
            assertEquals("Test dark-window task", task.name)
        }

    @Test
    fun `should run dark-window task executes and return outputJson`() =
        testApplication {
            application {
                module()
            }

            // 1. create a task
            val createResponse =
                client.post("/api/tasks/dark-window") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Runnable dark-window task",
                          "darkWindowRequest": {
                            "latitude": 10.3111,
                            "longitude": 123.8854,
                            "dateIso": "2025-08-12",
                            "timeZoneId": "Asia/Manila"
                          },
                          "frequency": "MANUAL"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, createResponse.status)

            val taskBody = createResponse.bodyAsText()
            val createdTask = json.decodeFromString<Task>(taskBody)

            // 2. run the task
            val runResponse =
                client.post("/api/tasks/${createdTask.id}/run") {
                    contentType(ContentType.Application.Json)
                }

            assertEquals(HttpStatusCode.OK, runResponse.status)

            val runBody = runResponse.bodyAsText()
            val runResult = json.decodeFromString<TaskRunResponse>(runBody)

            assertEquals(createdTask.id, runResult.task.id)
            assertEquals("SUCCESS", runResult.task.lastStatus.name)
            assertEquals(
                runResult.outputJson?.contains("window"),
                true,
                "outputJson should contain dark window data",
            )
        }
}
