# CLAUDE.md

Standing instructions for anyone, human or Claude, working in this repo.
It's loaded into every Claude session, so it stays short: rules and
surprises only. History, plans, and reference material live in the docs
listed below. The code is the ground truth if this file and the repo
ever disagree; fix this file when that happens.

## What this is

A solo hobby backend (Kotlin + Ktor on Cloud Run, Firestore) for
astrophotography: dark windows, meteor showers, a sky calendar, and a
task runner that runs these on a schedule and sends the results to
Discord, Telegram, or email. **The astronomy is all dummy data for now.**
It's a portfolio piece, so code quality and docs matter as much as
features.

## Commands

```bash
./gradlew clean build    # compile + ktlintCheck + test
./gradlew ktlintCheck    # lint only; CI fails on violations
./gradlew ktlintFormat   # auto-fix lint
./gradlew test           # all tests; no GCP credentials needed
./gradlew run            # start on :8080 (needs GCP credentials, see Gotchas)
```

`GET /health` → `OK` confirms the server is up.

## Where things are

| Need | Read |
|---|---|
| Layers, request flow, data model, deployment | `docs/ARCHITECTURE.md` |
| Naming, errors, logging, testing, **adding a task type** | `docs/CONVENTIONS.md` |
| Plans, data sources, **known gaps** | `docs/ROADMAP.md` |
| GCP, secrets, notification channels, test sandboxes | `docs/SETUP.md` |
| Something broke in production | `docs/RUNBOOKS.md` |
| What changed and why (root causes included) | `CHANGELOG.md` |

## Rules

- **Layering:** `api` → `core` ← `infra`. `core` never imports Ktor,
  Firestore or Logback. Only `infra` talks to external systems. Wiring
  is manual in `Application.module()` (`ServiceRegistry`); don't add a DI
  framework.
- **Tests:** use `kotlin.test.Test`, never `org.junit.Test`. There's no
  JUnit 4 engine, so those tests silently never run. Route tests call
  `testModule()`, never `module()`. Tests never touch real services: use
  fakes, Ktor's `MockEngine`, or GreenMail.
- **Task runs:** no try/catch inside a task's `execute<Type>`; the shared
  path in `TaskRunner.runTask` records failures. A date in a task payload
  is nullable and means "today" when the task runs.
- **Logging:** only through `StructuredLog` with an event from
  `LogEvents`. Never log secrets (webhook URLs, tokens, passwords).
- **Secrets** never go in the repo. `.env` is gitignored; `.env.example`
  holds commented placeholders only.
- **Dummy data** is always labelled as dummy, in code and in docs.
- **New endpoints** use the resource style (`/api/tasks/...`). Don't add
  a third route-naming scheme.
- **Before pushing:** `./gradlew ktlintCheck test`.
- **Commits:** `type: summary`, lowercase, imperative, no period. Types:
  `feat`, `fix`, `refactor`, `test`, `style`, `ci`, `build`, `chore`,
  `docs`.
- **CHANGELOG:** add entries under `Unreleased`, with the root cause for
  every fix. Never edit a released section.

## Gotchas

Things that surprise people the first time:

- **`./gradlew run` fails without GCP credentials.** It connects to real
  Firestore at startup. Run `gcloud auth application-default login` and
  set `GOOGLE_CLOUD_PROJECT` first (`docs/SETUP.md` § 6).
- **A half-configured notification channel stops startup**, on purpose,
  e.g. a Telegram token without a chat id. No channel variables at all
  is fine.
- **Logs are JSON in production and plain text in tests**
  (`src/main/resources/logback.xml` vs `src/test/resources/logback-test.xml`).
- **CI only runs on PRs into `main`.** In a stack of PRs, the ones
  further up get no CI until the PR below them merges, so run the checks
  locally.

## Keeping this file small

Before adding something here, ask whether every future session needs it.
History goes in `CHANGELOG.md`, plans and known gaps in
`docs/ROADMAP.md`, how-tos in `docs/`. Only rules and gotchas belong here.
