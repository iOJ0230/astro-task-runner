# Changelog

All notable changes to this project are documented here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this
project doesn't cut version tags yet, so entries are grouped by date
instead of a version number. Add new work under `Unreleased` as you go —
don't wait until a "release" to write it down.

## Unreleased

### Fixed
- **Integration tests no longer touch real Firestore.**
  `Application.module()` now takes an optional `taskRepositoryOverride:
  TaskRepository?` (default `null` → real Firestore, unchanged for
  `main()`). A new `Application.testModule()` test helper
  (`src/test/.../TestApplicationModule.kt`) injects
  `InMemoryTaskRepository` instead. All six `testApplication`-based route
  tests were switched from `module()` to `testModule()`. Root cause this
  fixes: CI's `ci.yml` has no GCP auth step, so these tests were only ever
  reliable by accident of whatever credentials happened to be ambient in
  the environment running them.
- **`TaskRunnerSchedulingTest`'s two tests were silently never running.**
  Found while verifying the fix above. The test used JUnit 4's
  `org.junit.Test` (available transitively via `ktor-server-tests-jvm`),
  but the project runs on JUnit Platform with no vintage engine
  registered — so both tests (covering `MANUAL` vs `DAILY` task
  scheduling) compiled successfully but were never discovered or executed
  by `./gradlew test`, in this session or, as far as the change history
  shows, ever. Fixed by switching to `kotlin.test.Test`, matching every
  other test file in the repo.
- **Structured JSON errors via `StatusPages`.** `Application.module()`
  installs `StatusPages` and maps `IllegalArgumentException` (task
  validation), `DateTimeException` (bad date/timezone input), and
  `JsonConvertException` (malformed request bodies) to `400` with a
  consistent `{"error": {"code", "message"}}` envelope
  (`api/model/ApiError.kt`); everything else falls through to a generic,
  non-leaky `500`. `TaskRoute.kt`'s missing-id/not-found responses were
  switched from plain text to the same envelope.
- **`POST /api/tasks/tick` now checks a shared secret** when
  `TASK_RUNNER_TICK_SECRET` is set (via the `X-Tick-Secret` header),
  401ing on a mismatch. No env var set → unchanged, unauthenticated
  behavior (local dev, tests).

### Changed
- Restored the executable bit on `gradlew` (it was checked in as a plain
  `644` file, so a fresh non-CI checkout needed a manual `chmod +x
  gradlew` before `./gradlew` would run at all; `ci.yml`/`cd.yml` paper
  over this with an explicit `chmod` step, which is why it went unnoticed).
- Removed the dead, unused local `CreateDarkWindowTaskRequest` from
  `TaskRoute.kt` (it shadowed the real one in `api/task/model/` and was
  never referenced).
- Moved `TaskListResponse`, `TaskRunResponse`, `TaskTickResponse` out of
  `TaskRoute.kt` into `api/task/model/TaskResponses.kt`, alongside the
  `Create*TaskRequest` DTOs they pair with.
- Deleted the test-only `TaskRunApiResponse` mirror class; tests now
  decode the real `api/task/model/TaskRunResponse` directly.
- Added KDoc to `InMemoryTaskRepository`, `DummyAstroMathService`, and
  `DummyAstroEventProvider` stating explicitly what each is for (and, for
  the two `Dummy*` classes, that they are not real astronomy).

### Added
- `docs/SETUP.md`: reconstructed GCP project / Firestore / CD-secrets
  setup guide (see that file's own note on provenance — it's derived from
  what the code and CI/CD workflows require, not a transcript of the
  original setup).

### Verified
- `./gradlew ktlintCheck test` and `./gradlew clean build shadowJar` both
  pass locally in a sandbox with **no GCP credentials at all** — the
  Firestore test-isolation fix is confirmed working, not just plausible.

## 2026-09-20 — Project documentation

Added `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/CONVENTIONS.md`, and this
`CHANGELOG.md`. No application code changed in this entry — see
`Unreleased` above for the follow-up fixes these docs' "Known gaps"
section prompted.

## 2026-01-05 — Firestore persistence

### Added
- `FirestoreTaskRepository`: tasks are now persisted to a Firestore
  `tasks` collection (one document per task, task serialized as a JSON
  string field) instead of only in memory.

### Fixed
- Several iterations on the Cloud Run CD workflow: explicit `gcloud`
  credential handling, avoided a `cloudresourcemanager` dependency in the
  CD sanity check, and disabled Cloud Build log streaming after it caused
  workflow failures.

## 2025-12-15 — Generic task creation + CD pipeline

### Added
- `POST /api/tasks/meteor-alert` — meteor-alert tasks join dark-window
  tasks as a persisted, schedulable task type.
- CD workflow (`cd.yml`): lint + test, then build via Cloud Build and
  deploy to Cloud Run on push to `main`.
- `ktlintCheck` wired into CI (`ci.yml`), console violation reporting
  configured in `build.gradle.kts`.

### Changed
- `TaskRunner` gained a generic `createTask(...)` helper (name, type,
  payload, serializer, frequency, hour, enabled); `createDarkWindowTask`
  became a thin wrapper over it instead of its own code path.
- Task request DTOs (`CreateDarkWindowTaskRequest`,
  `CreateMeteorAlertTaskRequest`) moved to `api/task/model/`.
- Codebase-wide ktlint formatting pass.

## 2025-12-08 — Containerized deploy

### Added
- Multi-stage `Dockerfile` (`eclipse-temurin:21-jdk` build →
  `eclipse-temurin:21-jre` runtime) building a shadow/fat jar via the
  Gradle Shadow plugin.
- `deploy.ps1`: manual one-button local deploy script (test → Cloud Build
  → Cloud Run deploy).
- CI workflow (`ci.yml`): build + test on push/PR to `main`.

## 2025-12-07 — Initial build-out

Everything from the first commit to the first `README` landed on this
single day.

### Added
- Ktor project scaffold with a `GET /health` endpoint.
- Core astro domain models and service interfaces: `AstroMathService`,
  `AstroEventService`, `DarkWindow`, `MeteorShowerEvent`.
- `DummyAstroEventProvider` — hardcoded Perseids/Geminids meteor shower
  data.
- `POST /api/run/astro/meteor-alert` and `POST /api/run/astro/sky-summary`
  "run now" endpoints; `SkySummaryService` orchestrating dark-window +
  meteor lookups into one response.
- Task runner: `Task`, `TaskType`, `TaskRepository` (in-memory at this
  point), `TaskRunner`; `POST /api/tasks/dark-window` to create a task and
  `POST /api/tasks/tick` to run all enabled tasks.
- Simple daily scheduling (`TaskFrequency.DAILY` + `preferredHourUtc`);
  `tick` only runs tasks that are actually due.
- Integration tests for the dark-window, meteor-alert, and sky-summary
  routes; scheduling tests for `TaskRunner` covering `MANUAL` vs `DAILY`
  behavior, using an injected `Clock` for determinism.
- Initial `README.md` describing the API surface and setup.

### Changed
- Project restructured from a flat layout into `api` / `core` / `infra`
  modules, with routes converted to modular functions registered from
  `Application.kt`.
- `Clock` injected into `TaskRunner` specifically to make scheduling
  tests deterministic.
