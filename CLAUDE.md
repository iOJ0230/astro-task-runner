# CLAUDE.md

This file orients a Claude Code session (or any future contributor) dropped
into this repo cold. Read this first, then `docs/ARCHITECTURE.md` and
`docs/CONVENTIONS.md` before making changes. `CHANGELOG.md` is the
historical record — update it, don't rewrite it.

## What this is

Astro Task Runner is a solo hobby backend: Kotlin + Ktor, deployed to Cloud
Run, backed by Firestore. It answers three astrophotography questions
(when's it dark, are any meteor showers coming, give me tonight's summary)
and wraps them in a minimal task-scheduling layer so the same computations
can run on a timer instead of only on-demand. The astronomy logic is
currently **all dummy/hardcoded** — see "Known gaps" below. This is a
portfolio piece as much as a working app, so code quality and docs matter
as much as features.

## Quick start

```bash
./gradlew clean build      # compile + ktlintCheck + test
./gradlew run               # start on :8080 (needs Firestore access — see below)
./gradlew ktlintCheck       # lint only; CI fails the build on violations
./gradlew test              # unit + Ktor integration tests
```

`GET /health` → `OK` is the fastest way to confirm the server is up. See
`docs/SETUP.md` for provisioning the GCP project, Firestore, and CD secrets
from scratch — the README alone doesn't cover that.

**Gotcha:** `Application.module()` wires a real `FirestoreTaskRepository`
via `FirestoreOptions.getDefaultInstance().service` by default, which needs
Application Default Credentials. Running `./gradlew run` locally without
`gcloud auth application-default login` (or `GOOGLE_APPLICATION_CREDENTIALS`)
will fail at startup. Tests don't hit this — see "Resolved" #1 below.

## Architecture at a glance

Four layers, one package root (`com.github.ioj0230.astro`):

```
api/    Ktor routes. Deserialize request → call a core service → respond.
core/   Domain models + service interfaces. No Ktor, no Firestore imports.
infra/  Interface implementations (dummy astronomy math, Firestore/in-memory
        task storage). This is the only layer allowed to know about
        external systems.
Application.kt   Manual dependency wiring (ServiceRegistry) + Ktor module setup.
```

Full detail, request/lifecycle diagrams, and the task state machine are in
`docs/ARCHITECTURE.md`. Naming, error-handling, and API-shape conventions
(and where the current code violates them) are in `docs/CONVENTIONS.md`.

There is no DI framework — `ServiceRegistry` in `Application.kt` is a plain
data class built once in `module()` and threaded through route functions.
Keep it that way until there's an actual reason (e.g. per-request scoping)
to reach for something heavier.

## Known gaps

The six gaps originally logged here (Firestore-coupled tests, missing
StatusPages, a dead duplicate DTO, an undocumented `InMemoryTaskRepository`,
an unauthenticated tick endpoint, and placeholder astronomy) have mostly
been addressed — see "Resolved" below for what changed and why. One item
(dummy astronomy math) is intentionally still open: it's a large, separate
piece of work, not a small fix, and forcing it into the same change as
everything else would have made that change hard to review.

**Still open:**

1. **All astronomy math is placeholder.** `DummyAstroMathService` assumes
   a fixed 20:00–03:00 dark window and derives "moon phase" from the day
   of the month; `DummyAstroEventProvider` hardcodes only Perseids and
   Geminids. Both classes now carry KDoc saying so explicitly. This is
   fine for scaffolding but should not be described as working astronomy
   anywhere in docs or demos without the "dummy" caveat. See roadmap
   item 1.
2. **No API versioning**, and the `/api/run/astro/*` vs `/api/tasks/*`
   naming split is unresolved — see `docs/CONVENTIONS.md`. Not urgent
   with zero external consumers, but don't add a third naming scheme.
3. **Two task-creation routes that are structurally one operation**
   (`/api/tasks/dark-window`, `/api/tasks/meteor-alert`) — not
   consolidated behind a generic `POST /api/tasks` yet. See roadmap
   item 3.
4. **Generic 500 for malformed non-JSON edge cases and unexpected
   exceptions.** `StatusPages` (see "Resolved" below) now maps the
   specific exceptions this codebase actually throws to 4xx; anything
   outside that list still falls through to a generic
   `INTERNAL_ERROR` 500. That's the correct default (don't leak internals
   on unexpected errors), just noting it's a deliberately short list, not
   exhaustive input validation.

## Resolved

1. **Firestore-coupled integration tests → fixed.** `Application.module()`
   now takes an optional `taskRepositoryOverride: TaskRepository?`
   parameter (default `null` → real Firestore, used by `main()`
   unchanged). A test-only `Application.testModule()` extension
   (`src/test/.../TestApplicationModule.kt`) passes
   `InMemoryTaskRepository()` instead. All six `testApplication` route
   tests now call `testModule()`, not `module()`, and run with zero GCP
   dependency — verified locally: `./gradlew test` passes in this sandbox,
   which has no GCP credentials at all.
2. **A second, unrelated test-discoverability bug found while verifying
   the fix above:** `TaskRunnerSchedulingTest` used JUnit 4's
   `org.junit.Test` (pulled in transitively via `ktor-server-tests-jvm`),
   but the project runs on JUnit Platform (`useJUnitPlatform()`) with no
   vintage engine configured. Result: its two tests — the ones covering
   `MANUAL` vs `DAILY` task scheduling, i.e. the core logic of the task
   runner — were silently never discovered or run, by Gradle or CI, since
   the test was added. Fixed by switching to `kotlin.test.Test`, matching
   every other test file in the repo. Confirmed via
   `find build/classes -iname '*TaskRunnerScheduling*'` (class compiled,
   proving it was never a compile failure) and `--tests` filtering before
   and after the fix. **Takeaway for future test files: always use
   `kotlin.test.Test`, never `org.junit.Test` — nothing in this project
   registers a JUnit 4 vintage engine.**
3. **`StatusPages` installed.** `Application.module()` now maps
   `IllegalArgumentException` (from `TaskRunner`'s `require()` validation),
   `DateTimeException` (bad `dateIso`/`timeZoneId`), and
   `JsonConvertException` (malformed request bodies) to `400` with a
   consistent `{"error": {"code", "message"}}` envelope
   (`api/model/ApiError.kt`), and everything else to a generic `500`
   without leaking the exception message. `TaskRoute.kt`'s missing-id and
   not-found responses were switched from plain text to the same envelope.
4. **Dead duplicate DTO removed.** The unused local
   `CreateDarkWindowTaskRequest` inside `TaskRoute.kt` is gone.
   `TaskListResponse`/`TaskRunResponse`/`TaskTickResponse` were moved out
   of that route file into `api/task/model/TaskResponses.kt`, matching
   where `Create*TaskRequest` DTOs already lived — and the test-only
   `TaskRunApiResponse` mirror class was deleted in favor of importing the
   real `TaskRunResponse` directly.
5. **`InMemoryTaskRepository` documented as intentionally prod-dead.**
   KDoc now says explicitly: not wired into `Application.module()`,
   exists only for `TaskRunnerSchedulingTest` and `testModule()`. Nobody
   should "clean it up," and nobody should wonder why it's unused in prod.
6. **`/api/tasks/tick` now checks a shared secret.** If the
   `TASK_RUNNER_TICK_SECRET` env var is set, the endpoint requires a
   matching `X-Tick-Secret` header and returns `401` otherwise. If the env
   var is unset (local dev, tests, and — until you configure it — prod),
   the check is skipped, so this is backward compatible until you opt in.
   See `docs/SETUP.md` for configuring this on Cloud Run + Cloud
   Scheduler.

## Roadmap (rough priority order)

1. Replace `DummyAstroMathService` with real sunset/sunrise + astronomical
   twilight + moon illumination calculations. *Capturing the Universe*
   (Woodhouse) and *The Beginner's Guide to Astrophotography* (Shaw) are
   good references for what actually matters to a shooter (Bortle-scale
   light pollution, moon illumination %, not just "is the sun down") —
   worth pulling from before over-engineering the math.
2. Replace hardcoded meteor showers with a small static dataset covering
   the full annual calendar (still not a live API, just more complete),
   sourced from a real almanac rather than two hardcoded events.
3. Consolidate the per-type task-creation routes (`/api/tasks/dark-window`,
   `/api/tasks/meteor-alert`) behind one generic `POST /api/tasks` that
   takes `type` in the body, now that `TaskRunner.createTask` is already
   generic. Two routes for what's structurally one operation is exactly
   the kind of shallow-wrapper duplication *A Philosophy of Software
   Design* warns about — the interface should be as generic as the
   implementation already is.
4. Namespace the API (`/api/v1/...`) before any breaking change, and pick
   one route-naming scheme — see `docs/CONVENTIONS.md` for the
   `/api/run/astro/*` vs `/api/tasks/*` inconsistency.
5. Upgrade `/api/tasks/tick` from a shared secret to something Cloud
   Scheduler supports natively (OIDC token + `run.invoker` IAM binding),
   once this is actually deployed behind Cloud Scheduler rather than
   hit manually.

## Adding a new task type (checklist)

1. Add the value to `TaskType` (`core/task/TaskType.kt`).
2. Add domain request/response models under `core/<domain>/` if new, with
   `@Serializable`.
3. Add a `run<Type>Task(...)` branch to `TaskRunner.runTask`'s `when`.
4. Add a `Create<Type>TaskRequest` under `api/task/model/`.
5. Add a route function under `api/task/` following the existing
   `darkWindowTaskRoute` / `meteorAlertTaskRoute` shape, and register it in
   `Application.module()`.
6. Add integration tests mirroring `DarkWindowTaskRouteTest` — call
   `testModule()`, not `module()` (see "Resolved" #1). And use
   `kotlin.test.Test`, not `org.junit.Test` (see "Resolved" #2) — Gradle
   will silently not run a JUnit 4-annotated test in this project.
7. Log the change in `CHANGELOG.md` under `Unreleased`.

## Working notes

- Commit style already in use (keep it): `type: summary`, types are
  `feat`, `fix`, `refactor`, `test`, `style`, `ci`, `build`, `chore`,
  `docs`. Lowercase, imperative mood, no period.
- `ktlintCheck` is enforced in CI (`ci.yml`) and blocks the build — run it
  locally before pushing, not after CI fails.
- When something breaks, record the root cause in `CHANGELOG.md`
  (`Fixed`), not just "fixed bug." A one-line root cause now saves a
  repeat investigation later — same reasoning as a blameless postmortem,
  just scaled down to a solo project with future-you (or future-Claude) as
  the audience.
- This project has no CLAUDE.md history before this commit — if you're a
  Claude session reading this for the first time, the repo's actual state
  (not this file) is ground truth if the two ever disagree. Update this
  file when you change something it describes.
