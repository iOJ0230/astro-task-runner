# Runbooks

Step-by-step fixes for things that go wrong once this is deployed. Each
runbook follows the same shape: **symptom → check → fix → verify**. They
assume `gcloud` is logged in to the project and these shell variables
are set:

```bash
REGION=asia-southeast1                      # whatever cd.yml deploys to
URL=$(gcloud run services describe astro-task-runner --region=$REGION --format='value(status.url)')
SECRET=...                                  # the TASK_RUNNER_TICK_SECRET value, if set
```

Two tools come up in almost every runbook:

- **Run history:** `curl "$URL/api/tasks/TASK_ID/runs?limit=5"`. Each run
  has its status, error, and per-channel `deliveries`.
- **Logs:** structured JSON, searchable by field. Runbook 10 covers how to
  query and trace them. The quickest: Logs Explorer with
  `jsonPayload.event="task.run.finished" jsonPayload.taskId="TASK_ID"`.

When a runbook teaches you something new, add it here. A runbook that's
out of date is worse than none.

---

## 1. Deploys are failing (CD is red)

**Symptom:** the "CD - Deploy to Cloud Run" workflow fails on pushes to
`main`.

**Check:** open the failed run in GitHub Actions and find the first
failing step.

| Failing step | Error contains | Cause |
|---|---|---|
| Build image with Cloud Build | `billing account ... disabled in state closed` | Billing account closed or unlinked. **This is the current state, since 2026-09-20** |
| Authenticate to Google Cloud | `invalid_grant`, key errors | `GCP_SA_KEY` GitHub secret missing, revoked, or rotated |
| Build image with Cloud Build | `PERMISSION_DENIED` | Deploy service account is missing `cloudbuild.builds.editor` or `storage.admin` |
| Deploy to Cloud Run | `PERMISSION_DENIED` | Deploy service account is missing `run.admin` or `iam.serviceAccountUser` |
| Lint and test | ktlint or test failure | A code problem. Reproduce with `./gradlew ktlintCheck test` |

**Fix (billing):** Console → Billing → link an active billing account to
the project, then add a budget alert (Billing → Budgets & alerts, e.g.
$1). For the other causes, see `docs/SETUP.md` steps 4–5.

**Verify:** re-run the failed workflow (Actions → the run → "Re-run all
jobs"), then `curl "$URL/health"` → `OK`.

## 2. I didn't get a notification I expected

**Check, in order:**

1. Did the task run at all? Look at `GET /api/tasks/TASK_ID/runs`. No
   recent run means the problem is scheduling: go to runbook 5.
2. Is the task's `notify` set so it should have sent? `NEVER` sends
   nothing. `ON_FAILURE` sends nothing when the run succeeded. See
   `GET /api/tasks/TASK_ID`.
3. Did the run try? A run's `deliveries` lists every channel it tried.
   Empty means the policy said no, or no channels are configured.
4. Did a channel fail? Any delivery with `"success": false` has the
   reason in `error`. Go to runbook 3.
5. Are the channels configured at all? The startup log line
   `Notification channels enabled: [...]` lists them.

**Verify:** send a test message to every channel:

```bash
curl -X POST "$URL/api/notifications" -H 'Content-Type: application/json' \
  -H "X-Tick-Secret: $SECRET" -d '{}'
```

## 3. A notification channel is failing

**Symptom:** deliveries show `"success": false`, or logs show
`Notification via <channel> failed`.

| Channel | Error | Cause | Fix |
|---|---|---|---|
| discord | `responded 404` | The webhook was deleted | Create a new webhook, then rotate `discord-webhook-url` (runbook 4) |
| discord | `responded 429` | Rate limited | Too many messages too fast. At this volume it should clear by itself |
| telegram | `responded 401` | Bot token revoked or regenerated in @BotFather | Rotate `telegram-bot-token` (runbook 4) |
| telegram | `responded 400 ... chat not found` | Wrong chat id, or you never messaged the bot | Message the bot, then redo `getUpdates` (`docs/SETUP.md` § 9) and update `TELEGRAM_CHAT_ID` |
| telegram | `responded 403 ... blocked by the user` | You blocked the bot | Unblock it in Telegram |
| email | `AuthenticationFailedException` | The App Password was revoked. **Changing your Google password revokes all App Passwords** | Create a new App Password and rotate `smtp-password` (runbook 4) |
| any | `request failed (…TimeoutException)` | The channel's API was slow or down | Usually transient. If it persists, check the provider's status page |

The other channels keep working while one is broken. That's by design;
a single broken channel never fails the task.

**Verify:** the test message from runbook 2.

## 4. Rotate a secret

Use this when a token is leaked, revoked, or replaced.

```bash
# 1. Add the new value as a new version
printf '%s' 'NEW_VALUE' | gcloud secrets versions add discord-webhook-url --data-file=-

# 2. Secrets are read when an instance starts, so roll a new revision
gcloud run services update astro-task-runner --region=$REGION \
  --update-secrets=DISCORD_WEBHOOK_URL=discord-webhook-url:latest

# 3. After verifying, disable the old version
gcloud secrets versions list discord-webhook-url
gcloud secrets versions disable OLD_VERSION_NUMBER --secret=discord-webhook-url
```

For a **leaked** credential, revoke it at the source first: delete the
Discord webhook, `/revoke` the token in @BotFather, or delete the Google
App Password. Rotating alone leaves the leaked value working.

For `GCP_SA_KEY` (GitHub, used by CD): create a new key (`docs/SETUP.md`
step 4), paste it into GitHub → Settings → Secrets → Actions, re-run CD,
then delete the old key with
`gcloud iam service-accounts keys delete OLD_KEY_ID --iam-account=...`.

**Verify:** the test message from runbook 2, and a green CD run for the
GitHub key.

## 5. Scheduled tasks aren't running

**Check:**

1. Is Cloud Scheduler calling `tick`?
   `gcloud scheduler jobs describe astro-task-runner-tick --location=$REGION`
   shows the last attempt and its status. Console → Cloud Scheduler
   shows the history.
2. Is `tick` rejecting it? A `401` means the job's `X-Tick-Secret`
   header doesn't match `TASK_RUNNER_TICK_SECRET`. This happens after
   rotating the secret without updating the job:
   `gcloud scheduler jobs update http astro-task-runner-tick --location=$REGION --update-headers="X-Tick-Secret=NEW_VALUE"`.
3. Is the task due? Only `enabled` tasks with `"frequency": "DAILY"` run
   on tick, once per UTC day, at or after `preferredHourUtc`. `MANUAL`
   tasks never run on tick. Note it's **UTC**: 08:00 in Manila is
   `preferredHourUtc: 0`.
4. Is the service up at all? `curl "$URL/health"`. If not, see runbooks
   1 and 7.

**Verify:** trigger it by hand and check the response lists your task:

```bash
curl -X POST "$URL/api/tasks/tick" -H "X-Tick-Secret: $SECRET"
```

## 6. A task keeps failing

**Check:** `GET /api/tasks/TASK_ID/runs` shows the `error` of each run.

| Error looks like | Cause | Fix |
|---|---|---|
| `Text 'xyz' could not be parsed` | Bad `dateIso` / `startDateIso` in the payload | Recreate the task with a valid date |
| `Unknown time-zone ID` | Bad `timeZoneId` | Recreate with an IANA zone like `Asia/Manila` |
| `All calendar sources failed: …` | Every calendar data source was down | Usually transient. See runbook 8 |
| `Task disabled` | The task is disabled | Expected. Re-enable it if you meant to run it |

There's no API to edit or delete a task yet. To stop a broken one, edit
its document in the Firestore console: set `"enabled": false` inside the
`taskJson` field. Then create a corrected task.

## 7. The service won't start after a config change

**Symptom:** a new revision fails to start, and Cloud Run keeps serving
the previous one or shows errors.

**Check:** the revision's logs, or Logs Explorer with
`jsonPayload.event="app.crashed"`, which holds the whole stack trace in
one entry. A common cause is
`Notification channel '…' is partially configured; missing: …`. That
means only some of a channel's variables are set, which fails startup on
purpose.

**Fix:** set the missing variable, or remove that channel's variables
entirely. To get the old revision back first:

```bash
gcloud run revisions list --service=astro-task-runner --region=$REGION
gcloud run services update-traffic astro-task-runner --region=$REGION \
  --to-revisions=PREVIOUS_REVISION_NAME=100
```

**Verify:** `curl "$URL/health"`, and the startup log line listing the
enabled channels.

## 8. The sky digest says "Incomplete"

**Symptom:** the notification ends with "⚠️ Incomplete — unavailable
right now: …", or a run's output has a non-empty `unavailableSources`.

**Cause:** that data source failed this run. The rest of the digest is
still correct; it's only missing that source's events.

**Check:** the run's logs for the provider's error. Is the source's
website up?

**Fix:** nothing, if it's a one-off. If it lasts more than a day or so,
the provider likely needs a fix (API change, new URL, expired key). See
`docs/ROADMAP.md` → "When a data source fails" for how providers should
behave.

## 9. Get told when the service itself breaks

Task failures reach you through `ON_FAILURE`. Some failures happen
outside a task: the service crashing, Firestore unreachable, or `tick`
itself erroring. No task notification can report those. To catch them,
make Cloud Monitoring email you:

1. Logs Explorer → query `resource.type="cloud_run_revision"
   resource.labels.service_name="astro-task-runner" severity>=ERROR`.
   This works because the app logs JSON with a real `severity`. Plain-text
   logs would have no severity, and the alert would never fire.
2. "Create alert" from that query, notifying your email.

It catches `app.crashed`, `request.unhandled_error` and
`task.run.history_write_failed`. Task failures are `WARNING`, so they
don't trigger it; those come through `ON_FAILURE` instead.

Optionally, make the Cloud Scheduler job's own failures alert too (an
alert on `cloud_scheduler_job` logs with `severity>=ERROR`).

## 10. Find and trace logs

Every log line is a JSON object. Cloud Logging stores its fields under
`jsonPayload`, so you can filter on any of them. Fields you'll use most:

| Field | Meaning |
|---|---|
| `event` | What happened, from `LogEvents` (e.g. `task.run.finished`) |
| `requestId` | The HTTP request being served. Shared by every line it caused |
| `taskId`, `runId` | Which task and which run. `runId` matches the run-history document |
| `status`, `error`, `trigger`, `durationMs` | Outcome of a run |
| `channel` | Notification channel, on delivery failures |

**Where:** Console → Logging → Logs Explorer, with the resource set to the
`astro-task-runner` Cloud Run service. Or from a terminal:

```bash
gcloud logging read 'resource.type="cloud_run_revision"
  resource.labels.service_name="astro-task-runner"
  jsonPayload.event="task.run.finished" jsonPayload.status="FAILED"' \
  --freshness=7d --limit=20 --format=json
```

**Useful queries** (add them to the base filter above):

| To find | Query |
|---|---|
| Every failed run | `jsonPayload.event="task.run.finished" jsonPayload.status="FAILED"` |
| Everything about one task | `jsonPayload.taskId="TASK_ID"` |
| Every failed delivery, by channel | `jsonPayload.event="notification.delivery.failed" jsonPayload.channel="telegram"` |
| Errors only | `severity>=ERROR` |
| Startup crashes | `jsonPayload.event="app.crashed"` |
| Slow runs | `jsonPayload.event="task.run.finished" jsonPayload.durationMs>5000` |

**Tracing one thing end to end.** Every entry point leads to the others:

1. **From a notification, or the run history:** take the run's `runId`
   (and `requestId`) from `GET /api/tasks/TASK_ID/runs`, then query
   `jsonPayload.runId="RUN_ID"`.
2. **From an API call you made:** every response carries an
   `X-Request-Id` header. Query `jsonPayload.requestId="THAT_ID"` to get
   every line the request produced. For a `tick`, that's every task it ran.
3. **From a Cloud Run request log** (Cloud Run writes one per request):
   with `GOOGLE_CLOUD_PROJECT` set on the service (`docs/SETUP.md` § 11),
   the app's lines are linked to it by trace. Expand the request entry,
   or click its trace id, to see them grouped.

**How long logs are kept:** 30 days, free (the `_Default` log bucket).
For longer, raise the bucket's retention (Logging → Logs Storage; days
beyond 30 are billed) or route the logs to BigQuery with a sink. For
"what happened to this task over months", use the run history in
Firestore, which keeps 90 days by default.
