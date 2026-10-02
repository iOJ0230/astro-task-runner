package com.github.ioj0230.astro.infra.notify

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotifierFactoryTest {
    private val httpClient = HttpClient(MockEngine { respondOk() })

    @Test
    fun `no variables means no channels`() {
        assertTrue(NotifierFactory.fromEnvironment(emptyMap(), httpClient).isEmpty())
    }

    @Test
    fun `discord is built when its webhook url is set`() {
        val channels =
            NotifierFactory.fromEnvironment(mapOf("DISCORD_WEBHOOK_URL" to "https://discord.test/hook"), httpClient)
                .map { it.channel }

        assertEquals(listOf("discord"), channels)
    }

    @Test
    fun `blank values count as unset`() {
        assertTrue(NotifierFactory.fromEnvironment(mapOf("DISCORD_WEBHOOK_URL" to " "), httpClient).isEmpty())
    }
}
