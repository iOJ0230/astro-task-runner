package com.github.ioj0230.astro

import com.github.ioj0230.astro.core.notify.Notifier
import com.github.ioj0230.astro.infra.task.InMemoryTaskRepository
import io.ktor.server.application.Application

/**
 * Test-only equivalent of [Application.module]: identical wiring, except
 * backed by [InMemoryTaskRepository] instead of Firestore. Every
 * `testApplication { application { ... } }` integration test should call
 * this instead of `module()` directly — `module()` with no override reaches
 * for real GCP credentials via `FirestoreOptions.getDefaultInstance()`,
 * which CI does not provide. See CLAUDE.md "Resolved" #1.
 *
 * Notifications default to none, so a developer's own channel env vars
 * never receive test traffic; pass fakes to assert on deliveries.
 */
fun Application.testModule(notifiers: List<Notifier> = emptyList()) =
    module(taskRepositoryOverride = InMemoryTaskRepository(), notifiersOverride = notifiers)
