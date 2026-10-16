package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.notify.Notification

/**
 * Turns a task's result into a human-readable [Notification]. Plain text
 * only, so the same message renders identically in Discord, Telegram and
 * email.
 */
internal object TaskNotifications {
    fun darkWindow(
        task: Task,
        response: DarkWindowResponse,
    ) = Notification(
        title = "🌌 Dark window — ${task.name}",
        body =
            listOf(
                response.window.description,
                "Start: ${response.window.startIso}",
                "End:   ${response.window.endIso}",
            ).joinToString("\n"),
    )

    fun meteorAlert(
        task: Task,
        response: MeteorAlertResponse,
    ) = Notification(
        title = "☄️ Meteor alert — ${task.name}",
        body =
            (
                listOf(response.summary) +
                    response.events.map {
                        "• ${it.name}: peak ${it.peakDateIso}, ~${it.zhr}/hr, radiant ${it.radiantConstellation}"
                    }
            ).joinToString("\n"),
    )

    fun failure(task: Task) =
        Notification(
            title = "⚠️ Task failed — ${task.name}",
            body = "${task.type} task ${task.id} failed: ${task.lastError ?: "unknown error"}",
        )
}
