package com.github.ioj0230.astro.infra.notify

import com.github.ioj0230.astro.core.notify.Notification
import com.github.ioj0230.astro.core.notify.Notifier
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val to: String,
)

/**
 * Plain-text email over SMTP with STARTTLS (port 587). Works with Gmail
 * using an App Password (not your normal password — see docs/SETUP.md).
 * Cloud Run blocks outbound port 25, so 587 is the port to use there.
 */
class EmailNotifier(
    private val config: SmtpConfig,
) : Notifier {
    override val channel = "email"

    private val session: Session =
        Session.getInstance(
            Properties().apply {
                put("mail.smtp.host", config.host)
                put("mail.smtp.port", config.port.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
                put("mail.smtp.writetimeout", "10000")
            },
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(config.username, config.password)
            },
        )

    override suspend fun send(notification: Notification) {
        val message =
            MimeMessage(session).apply {
                setFrom(InternetAddress(config.from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.to))
                subject = notification.title
                setText(notification.body, Charsets.UTF_8.name())
            }
        // Transport.send is blocking network I/O.
        withContext(Dispatchers.IO) { Transport.send(message) }
    }
}
