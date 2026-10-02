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
    fun `all three channels are built when fully configured`() {
        val env =
            mapOf(
                "DISCORD_WEBHOOK_URL" to "https://discord.test/hook",
                "TELEGRAM_BOT_TOKEN" to "123:abc",
                "TELEGRAM_CHAT_ID" to "42",
                "SMTP_HOST" to "smtp.gmail.com",
                "SMTP_USERNAME" to "me@example.com",
                "SMTP_PASSWORD" to "app-password",
                "NOTIFY_EMAIL_TO" to "me@example.com",
            )

        val channels = NotifierFactory.fromEnvironment(env, httpClient).map { it.channel }

        assertEquals(listOf("discord", "telegram", "email"), channels)
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

    private val localMailCatcher =
        mapOf(
            "SMTP_HOST" to "localhost",
            "SMTP_PORT" to "1025",
            "SMTP_USERNAME" to "dev",
            "SMTP_PASSWORD" to "dev",
            "NOTIFY_EMAIL_TO" to "me@example.com",
            "SMTP_STARTTLS" to "false",
        )

    @Test
    fun `STARTTLS can be turned off for a local mail catcher`() {
        assertEquals(listOf("email"), NotifierFactory.fromEnvironment(localMailCatcher, httpClient).map { it.channel })
    }

    @Test
    fun `STARTTLS cannot be turned off for a remote server`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                NotifierFactory.fromEnvironment(localMailCatcher + ("SMTP_HOST" to "smtp.gmail.com"), httpClient)
            }
        assertTrue(error.message!!.contains("unencrypted"), error.message)
    }

    @Test
    fun `SMTP_STARTTLS only accepts true or false`() {
        assertFailsWith<IllegalArgumentException> {
            NotifierFactory.fromEnvironment(localMailCatcher + ("SMTP_STARTTLS" to "no"), httpClient)
        }
    }
}
