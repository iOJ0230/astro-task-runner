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

class MeteorAlertTaskRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should create + run meteor-alert task and succeeds`() =
        testApplication {
            application {
                module()
            }

            // Create meteor-alert task
            val createResponse =
                client.post("/api/tasks/meteor-alert") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Meteor alert Cebu",
                          "meteorAlertRequest": {
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

            assertEquals(TaskType.METEOR_ALERT, createdTask.type)
            assertNotNull(createdTask.id)

            // Run the task
            val runResponse =
                client.post("/api/tasks/${createdTask.id}/run")

            assertEquals(HttpStatusCode.OK, runResponse.status)

            val runBody = runResponse.bodyAsText()

            val runResult =
                json.decodeFromString<TaskRunApiResponse>(
                    runBody,
                )

            // Assertions on execution result
            assertEquals(TaskStatus.SUCCESS, runResult.task.lastStatus)
            assertNotNull(runResult.outputJson)

            // Loose assertion: output should mention the domain object
            assertTrue(
                runResult.outputJson.contains("meteor", ignoreCase = true) ||
                    runResult.outputJson.contains("shower", ignoreCase = true),
                "outputJson should contain meteor alert information",
            )
        }

    @Test
    fun `should run disabled meteor-alert task and fails`() =
        testApplication {
            application {
                module()
            }

            val json = Json { ignoreUnknownKeys = true }

            // Create disabled task
            val createResponse =
                client.post("/api/tasks/meteor-alert") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Disabled meteor task",
                          "meteorAlertRequest": {
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

            val createdTask =
                json.decodeFromString<Task>(createResponse.bodyAsText())

            val runResponse =
                client.post("/api/tasks/${createdTask.id}/run")

            val runResult =
                json.decodeFromString<TaskRunApiResponse>(
                    runResponse.bodyAsText(),
                )

            assertEquals(TaskStatus.FAILED, runResult.task.lastStatus)
            assertEquals("Task disabled", runResult.task.lastError)
            assertEquals(null, runResult.outputJson)
        }
}
