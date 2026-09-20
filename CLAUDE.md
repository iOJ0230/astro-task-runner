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

`GET /health` → `OK` is the fastest way to confirm the server is up.

**Gotcha:** `Application.module()` wires a real `FirestoreTaskRepository`
via `FirestoreOptions.getDefaultInstance().service`, which needs Application
Default Credentials. Running locally without `gcloud auth
application-default login` (or `GOOGLE_APPLICATION_CREDENTIALS`) will fail
at startup. There is no local/emulator fallback wired in yet — see Known
gaps.

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

## Known gaps (fix these before adding features, or at least don't make them worse)

1. **Integration tests hit real Firestore.** `TaskRouteTest`,
   `DarkWindowTaskRouteTest`, and `MeteorAlertTaskRouteTest` all call
   `module()` directly, which pulls in `FirestoreTaskRepository`. CI's
   `ci.yml` has no GCP auth step, so these tests either fail closed or
   silently depend on ambient credentials in whatever environment runs
   them — that's not reproducible. Fix: make `module()` accept an injected
   `TaskRepository` (default to Firestore, override to
   `InMemoryTaskRepository` in tests), or extract a `testModule()`. This is
   the single highest-priority fix — it undermines the whole test suite's
   credibility.
2. **`ktor-server-status-pages-jvm` is a declared dependency that's never
   installed.** `Application.kt` never calls `install(StatusPages)`. Errors
   currently surface as raw exceptions or ad-hoc
   `call.respondText(..., status = ...)` calls scattered across routes
   (see `TaskRoute.kt`). Either wire up `StatusPages` for a consistent
   JSON error envelope, or drop the dependency — don't leave it dangling.
3. **Duplicate DTOs.** `TaskRoute.kt` declares its own local
   `CreateDarkWindowTaskRequest`, `TaskListResponse`, `TaskRunResponse`,
   `TaskTickResponse` — `CreateDarkWindowTaskRequest` shadows the real one
   in `api/task/model/` and is unused. Delete the local copy; it's dead
   code that will confuse the next person (or the next Claude session).
4. **`InMemoryTaskRepository` is prod-dead code.** Nothing in
   `Application.kt` wires it up anymore (Firestore replaced it); it only
   exists for `TaskRunnerSchedulingTest`. That's a legitimate use, but say
   so — a comment or this doc entry — so nobody "cleans it up" by mistake
   or, conversely, wonders why it's still there.
5. **`POST /api/tasks/tick` has no auth.** It's meant to be hit by Cloud
   Scheduler but is a public, unauthenticated endpoint that runs arbitrary
   stored tasks. Fine for a hobby project behind an obscure URL; not fine
   the moment this is presented as production-grade. At minimum, check a
   shared-secret header before this goes further.
6. **All astronomy math is placeholder.** `DummyAstroMathService` assumes
   a fixed 20:00–03:00 dark window and derives "moon phase" from the day
   of the month; `DummyAstroEventProvider` hardcodes only Perseids and
   Geminids. This is fine for scaffolding but should not be described as
   working astronomy anywhere in docs or demos without the "dummy" caveat.

## Roadmap (rough priority order)

1. Fix test isolation from Firestore (#1 above) — unblocks everything else.
2. Decide on and wire up a real error-handling strategy (`StatusPages`,
   consistent JSON error body: see `docs/CONVENTIONS.md`).
3. Replace `DummyAstroMathService` with real sunset/sunrise + astronomical
   twilight + moon illumination calculations. *Capturing the Universe*
   (Woodhouse) and *The Beginner's Guide to Astrophotography* (Shaw) are
   good references for what actually matters to a shooter (Bortle-scale
   light pollution, moon illumination %, not just "is the sun down") —
   worth pulling from before over-engineering the math.
4. Replace hardcoded meteor showers with a small static dataset covering
   the full annual calendar (still not a live API, just more complete),
   sourced from a real almanac rather than two hardcoded events.
5. Add a shared-secret or service-account check on `/api/tasks/tick`.
6. Consolidate the per-type task-creation routes (`/api/tasks/dark-window`,
   `/api/tasks/meteor-alert`) behind one generic `POST /api/tasks` that
   takes `type` in the body, now that `TaskRunner.createTask` is already
   generic. Two routes for what's structurally one operation is exactly
   the kind of shallow-wrapper duplication *A Philosophy of Software
   Design* warns about — the interface should be as generic as the
   implementation already is.
7. Namespace the API (`/api/v1/...`) before any breaking change, and pick
   one route-naming scheme — see `docs/CONVENTIONS.md` for the
   `/api/run/astro/*` vs `/api/tasks/*` inconsistency.

## Adding a new task type (checklist)

1. Add the value to `TaskType` (`core/task/TaskType.kt`).
2. Add domain request/response models under `core/<domain>/` if new, with
   `@Serializable`.
3. Add a `run<Type>Task(...)` branch to `TaskRunner.runTask`'s `when`.
4. Add a `Create<Type>TaskRequest` under `api/task/model/`.
5. Add a route function under `api/task/` following the existing
   `darkWindowTaskRoute` / `meteorAlertTaskRoute` shape, and register it in
   `Application.module()`.
6. Add integration tests mirroring `DarkWindowTaskRouteTest` — but see
   Known gaps #1 first, don't propagate the Firestore-in-tests problem.
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
