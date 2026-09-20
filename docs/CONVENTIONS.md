# Conventions

Rules for this codebase, and — deliberately — a record of where the
current code doesn't follow them yet. Documenting only the aspirational
state and hiding the gap makes the docs actively misleading the first time
someone (or some Claude session) reads a file that doesn't match. Where a
rule is violated today, it says so and points at the fix in `CLAUDE.md`.

## Package layout

- `api.*` — Ktor route functions only. A route function's job is: receive
  → validate/parse → call exactly one `core` service → respond. No
  business logic, no direct `infra` imports.
- `core.*` — domain models, service **interfaces**, and orchestration
  logic that doesn't touch the outside world (`SkySummaryService`,
  `TaskRunner`). Never imports Ktor or a Firestore type.
- `infra.*` — the only layer that imports external systems (Firestore,
  and eventually any real astronomy API client). Implements a `core`
  interface; nothing outside `infra` should import an `infra` class
  directly except `Application.kt`'s wiring.
- One domain concept per subpackage (`darkwindow`, `meteor`, `sky`,
  `task`, `math`) on both the `core` and `infra` side, named identically
  across the two so the implementation of a `core.math.AstroMathService`
  is easy to find in `infra.math`.

## Route naming — current inconsistency

Two different naming schemes exist side by side:

| Scheme | Examples | Style |
|---|---|---|
| Verb-in-path, "run now" | `POST /api/run/astro/dark-window`, `POST /api/run/astro/meteor-alert`, `POST /api/run/astro/sky-summary` | RPC-ish: `/run/<domain>/<action>` |
| Resource-oriented | `GET /api/tasks`, `GET /api/tasks/{id}`, `POST /api/tasks/{id}/run`, `POST /api/tasks/dark-window` | REST-ish: nouns + sub-resource actions |

Per Masse's *REST API Design Rulebook* and Geewax's *API Design
Patterns*, prefer resource nouns over verbs in the path, and reserve verbs
for cases with no natural resource (`/{id}/run` and `/tick` are legitimate
examples of that — "run this task" isn't creating/reading/updating a
resource, it's invoking an action on one, which both books treat as an
acceptable escape hatch from pure CRUD). The `/api/run/astro/*` family
should eventually become something like `POST
/api/astro/dark-window:compute` or simply be reframed as "create an
ephemeral, unpersisted result" — not a priority to change today, but new
endpoints should follow the `/api/tasks/*` resource style, not add a third
scheme.

Also note: `POST /api/tasks/dark-window` and `POST
/api/tasks/meteor-alert` are two routes doing the same thing
(`TaskRunner.createTask` is already fully generic over `TaskType`). See
`CLAUDE.md` roadmap item 3 — collapsing these to one `POST /api/tasks`
with `type` in the body is the more RESTful shape *and* removes
duplicated route code, which is the rare refactor that's a strict
improvement on both axes.

## No API versioning yet

There's no `/v1/` (or header/media-type versioning) anywhere. That's fine
while the API has no external consumers, but per *Building Microservices*
(Newman) and *Designing Web APIs* (Jin/Sahni/Shevat), the moment anything
outside this repo depends on a response shape, breaking changes need a
version boundary. Add `/api/v1/...` before the first external consumer,
not after — retrofitting a version prefix onto live traffic is strictly
more painful than starting with it.

## Request/response DTOs

- Every request/response type is a `@Serializable data class`, defined
  next to the domain it belongs to (`core/darkwindow/DarkWindow.kt` holds
  `DarkWindowRequest` *and* `DarkWindowResponse` in one file — that's the
  established pattern, not a one-off).
- Task-creation request DTOs live in `api/task/model/`, separate from the
  domain request they wrap (`CreateDarkWindowTaskRequest` wraps a
  `DarkWindowRequest`). Follow this split for new task types: the
  "envelope" (name, frequency, preferredHourUtc, enabled) is an API-layer
  concern; the payload inside it is a domain concern.
- **Don't redeclare a DTO locally inside a route function if a shared one
  already exists.** `TaskRoute.kt` used to do this (a local, unused
  `CreateDarkWindowTaskRequest` shadowing the real one in
  `api/task/model/`) — it's been removed. Response DTOs
  (`TaskListResponse`, `TaskRunResponse`, `TaskTickResponse`) also live in
  `api/task/model/TaskResponses.kt` now, not inline in the route file —
  same reasoning as the request-DTO split above.
- Defaults belong on the DTO (`frequency: TaskFrequency =
  TaskFrequency.MANUAL`), not re-derived in route handlers — this is
  already followed consistently, keep it that way.

## Error handling

`Application.module()` installs Ktor's `StatusPages` plugin and maps every
error response to one consistent JSON envelope
(`api/model/ApiError.kt`):

```json
{ "error": { "code": "TASK_NOT_FOUND", "message": "No task with id abc123" } }
```

Three exception types are mapped explicitly to `400`, because they're the
ones this codebase actually throws for bad client input:

- `IllegalArgumentException` — `TaskRunner`'s `require()` validation
  (missing/blank name, `preferredHourUtc` out of `0..23`, etc.).
- `DateTimeException` — `LocalDate.parse(dateIso)` / `ZoneId.of(timeZoneId)`
  failures (covers `DateTimeParseException` and `ZoneRulesException`, both
  subtypes).
- `JsonConvertException` — malformed/mistyped request bodies.

Everything else falls through to a generic `500` with a fixed
`INTERNAL_ERROR` message (never the raw exception message — don't leak
internals to the client) and logs the real cause server-side.

**When adding a new failure path:** if it's client-caused (bad input,
not-found, disabled resource), respond directly with `ApiErrorBody` and
the right status code (see `TaskRoute.kt`'s `missingIdError()` /
`taskNotFoundError()` for the pattern) rather than throwing — reserve
thrown exceptions + `StatusPages` for validation deep inside `core`
(`TaskRunner`, etc.) where the route layer doesn't have a natural place to
check first. If a new exception type becomes common enough to need its
own mapping, add it next to the existing `exception<...>` blocks in
`Application.kt` rather than letting it fall through to the generic 500.

## Kotlin style

- Formatting is enforced by **ktlint** (`org.jlleitschuh.gradle.ktlint`,
  configured in `build.gradle.kts`), checked in CI via `ktlintCheck`. Run
  `./gradlew ktlintCheck` before pushing; run `./gradlew ktlintFormat`
  locally to auto-fix.
- Trailing-comma, multi-line-trailing-lambda formatting for constructor
  calls is the house style already in use (see any `data class` call site
  with named args, e.g. `TaskRunResponse(task = ..., outputJson = ...)`)
  — ktlint enforces this, don't fight it.
- Interfaces for anything in `infra` that has more than one plausible
  implementation (`AstroMathService`, `AstroEventService`,
  `TaskRepository`) — even with only one real implementation today, this
  is what makes `Dummy*` swappable for a real integration later without
  touching `core` or `api`. Don't add an interface for something with
  no plausible second implementation (`SkySummaryService` is a concrete
  class, not an interface, because it orchestrates other services rather
  than wrapping an external system — keep that distinction).
- Favor "deep modules" (per Ousterhout, *A Philosophy of Software
  Design*): a small interface hiding real complexity, over a wide
  interface that just passes parameters through. `TaskRunner.createTask`
  is explicitly written this way already — its own doc comment says so.
  Match that bar for new additions: if a new method's body is one line
  that just forwards to another method, ask whether it needs to exist.

## Testing

- Unit tests for pure logic (`TaskRunnerSchedulingTest`) inject fakes
  directly — an inline `object : AstroMathService { ... }` stub and
  `InMemoryTaskRepository` — and a fixed `Clock`, never `Thread.sleep` or
  wall-clock time. Keep new scheduling/time-dependent tests deterministic
  the same way.
- Integration tests (`*RouteTest`) use Ktor's `testApplication { application
  { testModule() } }` (not `module()`) to exercise real routing +
  serialization without touching Firestore — `testModule()` (in
  `src/test/.../TestApplicationModule.kt`) is `module()` with
  `InMemoryTaskRepository` injected. Always use `testModule()` in new
  integration tests; calling `module()` directly reintroduces a hard
  dependency on live GCP credentials.
- Test names use backtick-quoted sentences (`` `should not run MANUAL
  tasks`() ``) — keep using full sentences, not `testX()`/camelCase names;
  it's the existing convention and it's more readable in CI output.
- **Always `import kotlin.test.Test`, never `org.junit.Test`.** The build
  runs on JUnit Platform (`useJUnitPlatform()` in `build.gradle.kts`) with
  no vintage engine configured, so a JUnit 4-style `@Test` (from
  `org.junit.Test`, available transitively via `ktor-server-tests-jvm`)
  compiles fine but is **silently never discovered or run** — no error,
  no warning, just a test that never executes. This happened for real:
  `TaskRunnerSchedulingTest` used `org.junit.Test` and both of its tests
  (covering `MANUAL` vs `DAILY` scheduling) had never run until this was
  caught. If a test file isn't showing up in `build/test-results/test/`
  after `./gradlew test`, check the `@Test` import first.

## Commit messages

Already-established, informal Conventional Commits:
`type: short imperative summary`, lowercase, no trailing period. Observed
types in this repo's history: `feat`, `fix`, `refactor`, `test`, `style`,
`ci`, `build`, `chore`, `docs`. Keep using this — it's what makes
`CHANGELOG.md` derivable from `git log` in the first place.

## Domain accuracy (when replacing dummy logic)

When `DummyAstroMathService` / `DummyAstroEventProvider` get replaced with
real calculations, the details that actually matter to a shooter — per
*Capturing the Universe* (Woodhouse), *The Beginner's Guide to
Astrophotography* (Shaw), and *NightWatch* (Dickinson) — are: astronomical
twilight (not just sunset), moon illumination percentage (not just
"phase"), and Bortle-scale light pollution context, not merely "is the sun
below the horizon." Model the domain types (`DarkWindow`,
`MoonPhase`-if-it-becomes-a-type) around what a photographer needs to
decide *when to shoot*, not around whatever a generic sun-position API
happens to return.
