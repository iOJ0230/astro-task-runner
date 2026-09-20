# Astro Task Runner 🌌

A small astrophotography automation backend built with *Kotlin + Ktor*.

It provides APIs for:
- ⭐ Dark windows (best shooting times)
- ☄️ Meteor shower alerts
- 🌙 Sky summaries for a given night
- 🧩 A simple pluggable *task runner* (/api/tasks/tick) for scheduling

This is a backend-only project intended for experimentation, learning, and future extension
(e.g. real astronomy APIs or Cloud Scheduler integration).

---

## Requirements

- JDK 21+
- Gradle (wrapper included)
- Kotlin 1.9+
- Ktor 2.3.x

---

## Running the Server
```bash

./gradlew clean build
./gradlew run
```

Server will start at:
```
http://localhost:8080
```

Health check:
```
curl http://localhost:8080/health
```

---

## Basic Usage (via curl or Postman)

🌑 Dark Window
```
POST /api/run/astro/dark-window
```

☄️ Meteor Alert
```
POST /api/run/astro/meteor-alert
```

🌌 Sky Summary
```
POST /api/run/astro/sky-summary
```

🗓️ Tasks
```
POST /api/tasks/dark-window   # create task
POST /api/tasks/{id}/run      # run task manually
POST /api/tasks/tick          # run all due tasks (for scheduling)
GET  /api/tasks               # list tasks
```

---

## Architecture (short overview)

```
com.github.ioj0230.astro
├── api     # HTTP routes
├── core    # domain models + services (dark window, meteor, sky, tasks)
├── infra   # implementations (dummy math, dummy meteor data, in-memory tasks)
└── Application.kt  # Ktor setup + ServiceRegistry
```

The project is structured to support clean architecture:
* Swap real astronomy APIs behind interfaces later 
* Add storage engines or schedulers without touching domain logic 
* Make testing and extension easy

---

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — orientation for continuing this project (quick
  start, known gaps, roadmap)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layers, request flow,
  task lifecycle, and deployment diagrams
- [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) — naming, error-handling,
  and API-design conventions
- [`CHANGELOG.md`](CHANGELOG.md) — history of what's shipped

---

## Status

This project currently uses dummy astronomy logic and hardcoded meteor data
(Perseids & Geminids). Real astronomy APIs may be integrated next.


Pull requests and suggestions are welcome!