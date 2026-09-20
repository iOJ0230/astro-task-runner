# Changelog

All notable changes to this project are documented here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this
project doesn't cut version tags yet, so entries are grouped by date
instead of a version number. Add new work under `Unreleased` as you go —
don't wait until a "release" to write it down.

## Unreleased

Added by this session: `CLAUDE.md`, `docs/ARCHITECTURE.md`,
`docs/CONVENTIONS.md`, this `CHANGELOG.md`. No application code changed.

### Known issues (tracked, not yet fixed — see `CLAUDE.md` → Known gaps)
- Route-level integration tests (`TaskRouteTest`, `DarkWindowTaskRouteTest`,
  `MeteorAlertTaskRouteTest`) exercise `Application.module()` directly,
  which wires a real `FirestoreTaskRepository`. CI has no GCP credentials
  configured, so these tests are not reliably reproducible.
- `ktor-server-status-pages-jvm` is declared as a dependency but never
  installed; error responses are inconsistent (mix of plain text and
  unhandled-exception 500s).
- `TaskRoute.kt` declares a local, unused `CreateDarkWindowTaskRequest`
  that shadows the real one in `api/task/model/`.
- `POST /api/tasks/tick` has no authentication.

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
