package com.github.ioj0230.astro.core.notify

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationServiceTest {
    @Test
    fun `a failing channel does not stop the others`() =
        runBlocking {
            val discord = FakeNotifier("discord")
            val telegram = FakeNotifier("telegram", failWith = "bad token")
            val email = FakeNotifier("email")
            val service = NotificationService(listOf(discord, telegram, email))

            val deliveries = service.dispatch(Notification("Title", "Body"))

            assertEquals(
                listOf(
                    NotificationDelivery("discord", success = true),
                    NotificationDelivery("telegram", success = false, error = "bad token"),
                    NotificationDelivery("email", success = true),
                ),
                deliveries,
            )
            assertEquals(1, discord.sent.size)
            assertEquals(1, email.sent.size)
        }

    @Test
    fun `no channels means no deliveries`() =
        runBlocking {
            assertEquals(emptyList(), NotificationService(emptyList()).dispatch(Notification("t", "b")))
        }
}
