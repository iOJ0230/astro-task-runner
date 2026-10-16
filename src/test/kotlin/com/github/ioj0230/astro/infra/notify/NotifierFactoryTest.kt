package com.github.ioj0230.astro.infra.notify

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `discord and telegram are built when fully configured`() {
        val env =
            mapOf(
                "DISCORD_WEBHOOK_URL" to "https://discord.test/hook",
                "TELEGRAM_BOT_TOKEN" to "123:abc",
                "TELEGRAM_CHAT_ID" to "42",
            )

        val channels = NotifierFactory.fromEnvironment(env, httpClient).map { it.channel }

        assertEquals(listOf("discord", "telegram"), channels)
    }

    @Test
    fun `a half-configured channel fails loudly instead of being skipped`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                NotifierFactory.fromEnvironment(mapOf("TELEGRAM_BOT_TOKEN" to "123:abc"), httpClient)
            }
        assertTrue(error.message!!.contains("TELEGRAM_CHAT_ID"))
    }

    @Test
    fun `blank values count as unset`() {
        assertTrue(NotifierFactory.fromEnvironment(mapOf("DISCORD_WEBHOOK_URL" to " "), httpClient).isEmpty())
    }
}
