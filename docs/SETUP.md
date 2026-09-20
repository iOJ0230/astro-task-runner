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
code change needed.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `./gradlew run` hangs or fails immediately with a credentials/auth error | No ADC configured locally — step 6 |
| Cloud Run service returns 500s on every `/api/tasks/*` call, but `/health` works | Runtime service account is missing `roles/datastore.user` — step 4's second `add-iam-policy-binding` |
| `cd.yml` fails at "Authenticate to Google Cloud" | `GCP_SA_KEY` secret missing, malformed, or the key was revoked/rotated |
| `cd.yml` fails at "Build image with Cloud Build" | Deploy SA missing `cloudbuild.builds.editor` or `storage.admin` |
| `cd.yml` fails at "Deploy to Cloud Run" | Deploy SA missing `run.admin` or `iam.serviceAccountUser` |
| Firestore calls fail with a "project not found"-style error despite `gcloud auth application-default login` | Set `GOOGLE_CLOUD_PROJECT` explicitly — step 6 |
