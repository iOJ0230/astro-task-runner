package com.github.ioj0230.astro.api

import com.github.ioj0230.astro.core.calendar.AstroCalendarResponse
import com.github.ioj0230.astro.core.calendar.AstroEventCategory
import com.github.ioj0230.astro.testModule
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstroCalendarRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `returns the events in the requested window`() =
        testApplication {
            application { testModule() }

            val response = client.get("/api/calendar/events?timeZoneId=Asia/Manila&startDate=2026-10-01&days=31")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<AstroCalendarResponse>(response.bodyAsText())
            assertEquals("2026-10-31", body.endDateIso)
            assertEquals(17, body.events.size)
            assertEquals(4, body.events.count { it.category == AstroEventCategory.MOON_PHASE })
        }

    @Test
    fun `missing timeZoneId is a 400`() =
        testApplication {
            application { testModule() }

            val response = client.get("/api/calendar/events?startDate=2026-10-01")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("timeZoneId"))
        }

    @Test
    fun `bad time zone is a 400`() =
        testApplication {
            application { testModule() }

            val response = client.get("/api/calendar/events?timeZoneId=Mars/Olympus")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
