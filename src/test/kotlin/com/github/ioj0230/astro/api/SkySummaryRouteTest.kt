package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkySummaryRouteTest {
    @Test
    fun `sky-summary endpoint returns 200 and combined fields`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/run/astro/sky-summary") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "latitude": 10.3111,
                          "longitude": 123.8854,
                          "dateIso": "2025-08-10",
                          "timeZoneId": "Asia/Manila"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.bodyAsText()

            // We expect the summary to combine dark window + meteors + text
            assertTrue(body.contains("darkWindow"), "Response should contain 'darkWindow'")
            assertTrue(body.contains("meteors"), "Response should contain 'meteors'")
            assertTrue(body.contains("overallSummary"), "Response should contain 'overallSummary'")
        }
}
