package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DarkWindowRouteTest {

    @Test
    fun `should return 200 and window fields`() = testApplication {
        application {
            module() // use Application.module wiring
        }

        val response = client.post("/api/run/astro/dark-window") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "latitude": 10.3111,
                  "longitude": 123.8854,
                  "dateIso": "2025-08-12",
                  "timeZoneId": "Asia/Manila"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()

        // crude but effective checks: shape, not exact values
        assertTrue(body.contains("window"), "Response should contain 'window'")
        assertTrue(body.contains("startIso"), "Response should contain 'startIso'")
        assertTrue(body.contains("endIso"), "Response should contain 'endIso'")
    }
}