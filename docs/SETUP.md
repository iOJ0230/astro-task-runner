# Setup: GCP project, Firestore, and CI/CD secrets

**Provenance note:** this doc didn't exist before — the repo had run
instructions (`README.md`) and deploy *mechanics* (`Dockerfile`,
`deploy.ps1`, `.github/workflows/cd.yml`) but nothing documenting the
one-time GCP setup those mechanics assume already exists: a project,
Firestore enabled, a service account, and three GitHub Actions secrets.
This is reconstructed from what the code and workflows actually require —
not a transcript of whatever was originally clicked through in the GCP
console. If something here doesn't match reality, the deployed service's
actual GCP configuration is ground truth; update this file to match it.

Use this if you're setting the project up on a new machine, a new GCP
project, recovering lost access, or just want to know what "the API was
working and I could access it on the web" actually depended on.

## Where secrets go

There are two kinds of secret here, and they live in different places:

| Secret | Store it in | Why there |
|---|---|---|
| `GCP_PROJECT_ID`, `GCP_SA_KEY`, `GCP_REGION` | **GitHub** → repo Settings → Secrets and variables → Actions | Only `cd.yml` uses them, and only while a deploy is running |
| `DISCORD_WEBHOOK_URL`, `TELEGRAM_BOT_TOKEN`, `SMTP_PASSWORD`, `TASK_RUNNER_TICK_SECRET` | **GCP Secret Manager**, attached to the Cloud Run service (§ 9) | The running app needs them at all times. GitHub secrets don't exist outside a workflow run |
| `TELEGRAM_CHAT_ID`, `SMTP_HOST`, `SMTP_USERNAME`, `NOTIFY_EMAIL_TO` | Plain Cloud Run env vars | Not sensitive on their own |
| Anything, for local dev | Shell `export`s, or a `.env` file you `source` | `.env` is gitignored. Never commit a real value |

Could the app secrets go in GitHub and be passed in by `cd.yml` with
`--set-env-vars`? Technically yes, but they'd then sit in plain text in
the Cloud Run console and in every revision's config, and rotating one
would mean a redeploy. Secret Manager avoids both.

## What's required, and why

| Piece | Used by |
|---|---|
| A GCP project | Everything below |
| Firestore, Native mode | `FirestoreTaskRepository` (`FirestoreOptions.getDefaultInstance()`), at runtime |
| Cloud Build + Cloud Run APIs enabled | `cd.yml` (build image, deploy) |
| A service account with deploy permissions + a JSON key | `cd.yml`'s `GCP_SA_KEY` secret |
| `roles/datastore.user` on the **runtime** identity | The deployed Cloud Run service, to read/write Firestore |
| Three GitHub Actions secrets | `cd.yml` (`GCP_PROJECT_ID`, `GCP_SA_KEY`, `GCP_REGION`) |

Note the last two rows are about **two different identities**: the CI/CD
service account that builds and deploys, and the Cloud Run runtime
identity that the deployed service actually runs as. `cd.yml` doesn't pass
`--service-account` to `gcloud run deploy`, so the deployed service runs
as the project's **default compute service account** — that's the
identity that needs Firestore access, not necessarily the CD account (they
can be the same account if you prefer one SA for everything; just make
sure whichever one ends up as the Cloud Run runtime identity has
`roles/datastore.user`).

## 1. Create (or select) a GCP project

```bash
gcloud projects create YOUR_PROJECT_ID   # or: gcloud config set project YOUR_PROJECT_ID
gcloud config set project YOUR_PROJECT_ID
```

Billing must be enabled on the project (Cloud Run and Cloud Build both
require it).

## 2. Enable the required APIs

```bash
gcloud services enable \
  firestore.googleapis.com \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  containerregistry.googleapis.com
```

## 3. Create the Firestore database

```bash
gcloud firestore databases create --location=YOUR_REGION --type=firestore-native
```

Pick a region close to where Cloud Run will run (`deploy.ps1` and
`cd.yml` both default to `asia-southeast1` — match that or update both if
you pick elsewhere). `FirestoreTaskRepository` uses a single flat `tasks`
collection — no indexes or security rules to configure beyond the default.

## 4. Create a deploy service account

```bash
gcloud iam service-accounts create astro-task-runner-deployer \
  --display-name="astro-task-runner CI/CD"

SA_EMAIL="astro-task-runner-deployer@YOUR_PROJECT_ID.iam.gserviceaccount.com"

for role in roles/cloudbuild.builds.editor roles/run.admin \
            roles/iam.serviceAccountUser roles/storage.admin; do
  gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:$SA_EMAIL" --role="$role"
done
```

- `cloudbuild.builds.editor` — `gcloud builds submit` in `cd.yml`.
- `run.admin` — `gcloud run deploy`.
- `iam.serviceAccountUser` — lets the deploy SA act as the runtime SA
  Cloud Run uses.
- `storage.admin` — Cloud Build needs a bucket for build context/logs and
  pushing to `gcr.io`.

Then grant Firestore access to whichever identity actually runs the
deployed service — the **default compute service account**, unless you've
changed that:

```bash
PROJECT_NUMBER=$(gcloud projects describe YOUR_PROJECT_ID --format='value(projectNumber)')
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/datastore.user"
```

Generate a JSON key for the deploy SA (this is what goes into the
`GCP_SA_KEY` GitHub secret):

```bash
gcloud iam service-accounts keys create astro-task-runner-deployer-key.json \
  --iam-account="$SA_EMAIL"
```

A downloadable long-lived JSON key is what `cd.yml` currently expects
(`credentials_json` in the `google-github-actions/auth` step). Workload
Identity Federation is the more secure alternative (no long-lived key to
leak or rotate) but is a bigger change to `cd.yml` — worth doing before
this is anything more than a hobby project, not required to get it
working today.

## 5. Configure GitHub Actions secrets

In the repo's Settings → Secrets and variables → Actions, add:

| Secret | Value |
|---|---|
| `GCP_PROJECT_ID` | `YOUR_PROJECT_ID` |
| `GCP_SA_KEY` | the full contents of `astro-task-runner-deployer-key.json` |
| `GCP_REGION` | e.g. `asia-southeast1` (must match where you created Firestore) |

Once set, `cd.yml` runs automatically on every push to `main`.

## 6. Local development

`./gradlew run` builds a real `FirestoreTaskRepository` at startup (see
`CLAUDE.md`'s "Gotcha"), so it needs Application Default Credentials
locally:

```bash
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

If Firestore calls fail with a project-not-found-style error even after
that, `FirestoreOptions.getDefaultInstance()` couldn't infer the project
from ADC — set it explicitly:

```bash
export GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID
```

Alternative: a downloaded key file + `GOOGLE_APPLICATION_CREDENTIALS`
pointing at it, same as CI uses.

`./gradlew test` does **not** need any of this — integration tests run
against `Application.testModule()` (`InMemoryTaskRepository`), not real
Firestore.

## 7. Manual deploy (`deploy.ps1`)

`deploy.ps1` is a Windows/PowerShell fallback that does the same three
steps as `cd.yml` (test → Cloud Build → Cloud Run deploy) using whatever
`gcloud` identity is active locally — that identity needs the same roles
as the deploy service account above. Update `$PROJECT_ID`, `$REGION`,
`$SERVICE` at the top of the script to match your project before running
it. Keep this in sync with `cd.yml` manually if either changes — they are
two independent copies of the same deploy steps, not shared code.

## 8. Securing `POST /api/tasks/tick`

New in this pass: setting `TASK_RUNNER_TICK_SECRET` makes `/api/tasks/tick`
require a matching `X-Tick-Secret` header (see `CLAUDE.md` → "Resolved" #6).
To turn it on:

```bash
gcloud run services update astro-task-runner \
  --region=YOUR_REGION \
  --set-env-vars="TASK_RUNNER_TICK_SECRET=$(openssl rand -hex 32)"
```

If you're driving `tick` from Cloud Scheduler, add the header to the HTTP
target:

```bash
gcloud scheduler jobs create http astro-task-runner-tick \
  --location=YOUR_REGION \
  --schedule="0 * * * *" \
  --uri="https://YOUR_CLOUD_RUN_URL/api/tasks/tick" \
  --http-method=POST \
  --headers="X-Tick-Secret=THE_SAME_SECRET_VALUE"
```

Until you do this, `tick` stays unauthenticated (matches today's
behavior) — set the env var whenever you're ready to lock it down, no
code change needed. (The command above sets it as a plain env var to
keep the example short. Prefer storing it in Secret Manager like the § 9
secrets: `--set-secrets=TASK_RUNNER_TICK_SECRET=tick-secret:latest`.)

The same secret also guards `POST /api/notifications` (§ 9), which sends
messages to your channels. Set it before configuring any channel on a
publicly reachable service, or anyone with the URL can spam you.

## 9. Notification channels (Discord, Telegram, email)

Each channel is turned on by setting its environment variables. If none
are set, the channel is skipped. If only some are set, **startup fails**
with a message naming the missing variable. That's deliberate: a typo
should break the deploy, not quietly stop your alerts. The startup log
line `Notification channels enabled: [...]` shows which channels are
live.

| Channel | Variables |
|---|---|
| Discord | `DISCORD_WEBHOOK_URL` |
| Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` |
| Email | `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `NOTIFY_EMAIL_TO`; optional `SMTP_PORT` (default 587), `NOTIFY_EMAIL_FROM` (default `SMTP_USERNAME`), `SMTP_STARTTLS` (default `true`; `false` only for a local mail catcher) |

**Discord:** open the Discord server you want alerts in, go to Server
Settings → Integrations → Webhooks → New Webhook, pick the channel, and
use **Copy Webhook URL**. That URL is the whole credential.

**Telegram:**
1. Message `@BotFather`, send `/newbot`, and follow the prompts. It
   replies with the bot token.
2. Send any message to your new bot. The bot can't message you first.
3. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser.
   Your chat id is `result[0].message.chat.id`.

**Email (Gmail):** Gmail won't accept your normal password over SMTP.
1. Turn on 2-Step Verification for the Google account.
2. Create an App Password at myaccount.google.com/apppasswords.
3. Set `SMTP_HOST=smtp.gmail.com`, `SMTP_USERNAME=<your gmail>`,
   `SMTP_PASSWORD=<the 16-character app password>`, and `NOTIFY_EMAIL_TO`
   to wherever you want the alerts.

Use port 587 (STARTTLS, the default). Cloud Run blocks outbound port 25.

**On Cloud Run, store the secrets in Secret Manager** (see "Where secrets
go" at the top):

```bash
gcloud services enable secretmanager.googleapis.com
printf '%s' 'https://discord.com/api/webhooks/...' | gcloud secrets create discord-webhook-url --data-file=-
printf '%s' '123456:ABC...' | gcloud secrets create telegram-bot-token --data-file=-
printf '%s' 'abcd efgh ijkl mnop' | gcloud secrets create smtp-password --data-file=-

PROJECT_NUMBER=$(gcloud projects describe YOUR_PROJECT_ID --format='value(projectNumber)')
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud run services update astro-task-runner --region=YOUR_REGION \
  --set-secrets="DISCORD_WEBHOOK_URL=discord-webhook-url:latest,TELEGRAM_BOT_TOKEN=telegram-bot-token:latest,SMTP_PASSWORD=smtp-password:latest" \
  --update-env-vars="TELEGRAM_CHAT_ID=...,SMTP_HOST=smtp.gmail.com,SMTP_USERNAME=you@gmail.com,NOTIFY_EMAIL_TO=you@gmail.com"
```

`cd.yml`'s `gcloud run deploy --image ...` keeps env vars and secrets
already set on the service, so this is a one-time step, not something CD
has to know about. Secrets are read when an instance starts, so after
adding a new secret version, roll a new revision to pick it up, e.g.
`gcloud run services update astro-task-runner --region=YOUR_REGION
--update-secrets=DISCORD_WEBHOOK_URL=discord-webhook-url:latest`.

**Local dev:** put the same variables in a `.env` file (gitignored) and
`set -a; source .env; set +a` before `./gradlew run`. `.env.example` is a
commented starting point with the test setups below.

**Testing without spamming your real channels.** Use separate test
destinations, set only in your local `.env` or in a test deployment.
Never point tests at the channels your real alerts go to.

| Channel | Test destination | Notes |
|---|---|---|
| Email | **Mailtrap Email Testing** (`sandbox.smtp.mailtrap.io`, port 2525 or 587) | Catches every message in a web inbox, and nothing is delivered to anyone. Free tier is fine for this. Works with the notifier as-is (STARTTLS) |
| Email | **Mailpit** on your machine (`localhost:1025`, web UI on `:8025`) | No account, fully offline. Needs `SMTP_STARTTLS=false`, which the app only accepts for `localhost` |
| Discord | A **private test server or channel** with its own webhook | There's no Mailtrap-style sandbox for Discord, but a test server is free and takes two minutes. Delete its webhook when done |
| Telegram | A **separate test bot** from @BotFather, messaging you or a private test group | Telegram has an official test environment, but it needs a separate test account. A second bot is simpler |

To inspect exactly what the app sends to Discord, set
`DISCORD_WEBHOOK_URL` to a request-catcher URL from a service like
webhook.site. It shows the raw JSON body. Only send test text this way,
since the service can read it.

Automated tests never touch any of these: `./gradlew test` uses fakes,
Ktor's `MockEngine`, and an in-process GreenMail SMTP server.

**Check the setup** without creating a task:

```bash
curl -X POST https://YOUR_CLOUD_RUN_URL/api/notifications \
  -H 'Content-Type: application/json' -H "X-Tick-Secret: $SECRET" -d '{}'
# → {"deliveries":[{"channel":"discord","success":true}, ...]}
```

**Choose what each task sends** with `"notify"` in the task-creation body:

| `notify` | Sends |
|---|---|
| `"NEVER"` (default) | Nothing |
| `"ON_FAILURE"` | Only when a run fails, with the error. Good for "tell me if it breaks" |
| `"ALWAYS"` | Every run: the result on success, the error on failure |

## 10. Run history and retention

Every task run is stored in Firestore at `tasks/{taskId}/runs/{runId}`:
status, error, output, trigger (`MANUAL` or `TICK`), timings, and the
result of each notification channel. Read it with:

```bash
curl https://YOUR_CLOUD_RUN_URL/api/tasks/TASK_ID/runs?limit=20
```

or browse it in the console (Firestore → `tasks` → a task → `runs`).

Runs carry an `expireAt` timestamp 90 days after they start. To have
Firestore delete them automatically (free, and it keeps storage flat),
turn on the TTL policy once:

```bash
gcloud firestore fields ttls update expireAt \
  --collection-group=runs --enable-ttl
```

Without it, runs are kept forever. At one run per task per day that's
small, but it never stops growing.

Every run also writes one log line (`INFO` on success, `WARN` with the
error on failure, plus a `WARN` per failed channel). In the console:
Cloud Run → astro-task-runner → Logs, or Logs Explorer with
`resource.type="cloud_run_revision" textPayload:"Task "`.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `./gradlew run` hangs or fails immediately with a credentials/auth error | No ADC configured locally — step 6 |
| Cloud Run service returns 500s on every `/api/tasks/*` call, but `/health` works | Runtime service account is missing `roles/datastore.user` — step 4's second `add-iam-policy-binding` |
| `cd.yml` fails at "Authenticate to Google Cloud" | `GCP_SA_KEY` secret missing, malformed, or the key was revoked/rotated |
| `cd.yml` fails at "Build image with Cloud Build" | Deploy SA missing `cloudbuild.builds.editor` or `storage.admin` |
| `cd.yml` fails at "Deploy to Cloud Run" | Deploy SA missing `run.admin` or `iam.serviceAccountUser` |
| Firestore calls fail with a "project not found"-style error despite `gcloud auth application-default login` | Set `GOOGLE_CLOUD_PROJECT` explicitly — step 6 |
| Startup fails with "Notification channel '...' is partially configured" | Some, not all, of that channel's variables are set — § 9 |
| `POST /api/notifications` returns `409 NO_NOTIFICATION_CHANNELS` | No channel variables set on this service — § 9 |
| A delivery shows `"success": false` with `telegram responded 400: ... chat not found` | Wrong `TELEGRAM_CHAT_ID`, or you never messaged the bot first — § 9 |
| Email delivery fails with an authentication error | Using the normal Gmail password instead of an App Password, or 2-Step Verification is off — § 9 |
