package com.github.ioj0230.astro.core.task

import com.github.ioj0230.astro.core.calendar.AstroCalendarResponse
import com.github.ioj0230.astro.core.calendar.AstroEventCategory
import com.github.ioj0230.astro.core.darkwindow.DarkWindowResponse
import com.github.ioj0230.astro.core.meteor.MeteorAlertResponse
import com.github.ioj0230.astro.core.notify.Notification
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a task's result into a human-readable [Notification]. Plain text
 * only, so the same message renders identically in Discord, Telegram and
 * email.
 */
internal object TaskNotifications {
    private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

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

    fun astroCalendar(
        task: Task,
        response: AstroCalendarResponse,
    ): Notification {
        val start = LocalDate.parse(response.startDateIso).format(SHORT_DATE)
        val end = LocalDate.parse(response.endDateIso).format(SHORT_DATE)
        val lines =
            if (response.events.isEmpty()) {
                listOf("Nothing on the calendar this period.")
            } else {
                response.events.map { event ->
                    val date = LocalDate.parse(event.dateIso).format(SHORT_DATE)
                    val details = event.details?.let { " — $it" } ?: ""
                    "${icon(event.category)} $date · ${event.title}$details"
                }
            }
        val sources = response.events.map { it.source }.distinct()
        val footer = if (sources.isEmpty()) emptyList() else listOf("", "Source: ${sources.joinToString("; ")}")

        return Notification(
            title = "🔭 Sky calendar $start – $end — ${task.name}",
            body = (lines + footer).joinToString("\n"),
        )
    }

    fun failure(task: Task) =
        Notification(
            title = "⚠️ Task failed — ${task.name}",
            body = "${task.type} task ${task.id} failed: ${task.lastError ?: "unknown error"}",
        )

    private fun icon(category: AstroEventCategory): String =
        when (category) {
            AstroEventCategory.MOON_PHASE -> "🌙"
            AstroEventCategory.METEOR_SHOWER -> "☄️"
            AstroEventCategory.OPPOSITION -> "🪐"
            AstroEventCategory.CLOSE_APPROACH -> "🔭"
            AstroEventCategory.WELL_PLACED -> "✨"
        }
}
