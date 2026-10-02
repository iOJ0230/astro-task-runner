package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notification
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.MessagingException
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Sends real SMTP traffic to an in-process GreenMail server. */
class EmailNotifierTest {
    private val greenMail = GreenMail(ServerSetupTest.SMTP)

    private fun config(password: String = "app-password") =
        SmtpConfig(
            host = "127.0.0.1",
            port = ServerSetupTest.SMTP.port,
            username = "me@example.com",
            password = password,
            from = "me@example.com",
            to = "alerts@example.com",
            startTls = false,
        )

    @BeforeTest
    fun start() {
        greenMail.start()
        greenMail.setUser("me@example.com", "me@example.com", "app-password")
    }

    @AfterTest
    fun stop() = greenMail.stop()

    @Test
    fun `delivers a plain-text email with the title as subject`() =
        runBlocking {
            EmailNotifier(config()).send(Notification("Orionids peak tonight", "Best after midnight"))

            val message = greenMail.receivedMessages.single()
            assertEquals("Orionids peak tonight", message.subject)
            assertEquals("alerts@example.com", message.allRecipients.single().toString())
            assertEquals("Best after midnight", (message.content as String).trim())
        }

    @Test
    fun `wrong password fails the send`() {
        assertFailsWith<MessagingException> {
            runBlocking { EmailNotifier(config(password = "wrong")).send(Notification("t", "b")) }
        }
    }
}
