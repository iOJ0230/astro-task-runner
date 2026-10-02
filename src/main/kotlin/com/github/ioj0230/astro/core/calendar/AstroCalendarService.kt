package com.github.ioj0230.astro.core.calendar

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Upper bound on [AstroCalendarRequest.days]; roughly one quarter. */
const val MAX_CALENDAR_DAYS = 92

/** Every provider failed, so there is no honest digest to return. */
class AllCalendarSourcesFailedException(
    failures: List<String>,
) : RuntimeException("All calendar sources failed: ${failures.joinToString("; ")}")

/**
 * Merges events from every [AstroCalendarProvider].
 *
 * Providers fail independently. If one throws (an external API is down,
 * a dataset is missing), its events are left out and its name is listed
 * in [AstroCalendarResponse.unavailableSources], so the digest says what
 * it's missing instead of silently looking like a quiet sky. If *every*
 * provider fails, an empty "nothing happening" digest would be a lie, so
 * this throws [AllCalendarSourcesFailedException] and the task is recorded
 * as FAILED.
 */
class AstroCalendarService(
    private val providers: List<AstroCalendarProvider>,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun upcoming(request: AstroCalendarRequest): AstroCalendarResponse {
        require(request.days in 1..MAX_CALENDAR_DAYS) { "days must be within 1..$MAX_CALENDAR_DAYS" }
        val zoneId = ZoneId.of(request.timeZoneId)
        val start = request.startDateIso?.let(LocalDate::parse) ?: LocalDate.now(clock.withZone(zoneId))
        val end = start.plusDays(request.days - 1L)

        val results = providers.map { provider -> provider to runCatching { provider.events(start, end, zoneId) } }
        val failures = results.mapNotNull { (provider, result) -> result.exceptionOrNull()?.let { provider.name to it } }
        if (providers.isNotEmpty() && failures.size == providers.size) {
            throw AllCalendarSourcesFailedException(failures.map { (name, e) -> "$name: ${e.message}" })
        }

        val events =
            results
                .flatMap { (_, result) -> result.getOrDefault(emptyList()) }
                // Don't trust every provider to filter correctly.
                .filter { LocalDate.parse(it.dateIso) in start..end }
                .distinct()
                .sortedWith(compareBy({ it.dateIso }, { it.category }, { it.title }))

        val unavailable = failures.map { (name, _) -> name }
        val summary =
            buildString {
                append(
                    if (events.isEmpty()) {
                        "No sky events found between $start and $end."
                    } else {
                        "${events.size} sky event(s) between $start and $end."
                    },
                )
                if (unavailable.isNotEmpty()) append(" Incomplete: ${unavailable.joinToString()} unavailable.")
            }

        return AstroCalendarResponse(
            startDateIso = start.toString(),
            endDateIso = end.toString(),
            timeZoneId = request.timeZoneId,
            events = events,
            summary = summary,
            unavailableSources = unavailable,
        )
    }
}
