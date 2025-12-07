package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeteorAlertRouteTest {

    @Test
    fun `should return 200 and meteor fields`() = testApplication {
        application {
            module() // use the real Ktor wiring
        }

        val response = client.post("/api/run/astro/meteor-alert") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "latitude": 10.3111,
                  "longitude": 123.8854,
                  "dateIso": "2025-08-10",
                  "timeZoneId": "Asia/Manila"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()

        // Crude but effective shape checks
        assertTrue(body.contains("events"), "Response should contain 'events'")
        assertTrue(body.contains("summary"), "Response should contain 'summary'")

        // Optional: if dummy data includes Perseids/Geminids, we can loosely check names:
        // assertTrue(body.contains("Perseids") || body.contains("Geminids"),
        //     "Expected dummy meteor shower names in response")
    }
}