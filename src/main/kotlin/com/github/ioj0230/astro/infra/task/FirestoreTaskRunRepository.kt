package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.notify.NotificationDelivery
import com.github.ioj0230.astro.core.task.TaskRun
import com.github.ioj0230.astro.core.task.TaskRunRepository
import com.github.ioj0230.astro.core.task.TaskRunTrigger
import com.github.ioj0230.astro.core.task.TaskStatus
import com.github.ioj0230.astro.core.task.TaskType
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** Runs older than this are deleted by Firestore's TTL policy on `expireAt`. */
val TASK_RUN_RETENTION: Duration = Duration.ofDays(90)

/**
 * Stores each run as `tasks/{taskId}/runs/{runId}`.
 *
 * Unlike [FirestoreTaskRepository]'s single JSON blob, runs are stored as
 * real fields so they can be read and filtered in the Firestore console,
 * and ordered by the `startedAt` timestamp. (Ordering by the ISO string
 * would be wrong: `OffsetDateTime.toString()` drops `:00` seconds, so
 * "10:00Z" would sort after "10:00:05Z".)
 *
 * A subcollection keeps "latest runs for one task" a single-field query,
 * which Firestore indexes automatically — no composite index to create.
 * `expireAt` is for a TTL policy (docs/SETUP.md § 10); without the policy,
 * runs are simply kept forever.
 */
class FirestoreTaskRunRepository(
    private val firestore: Firestore,
    private val json: Json,
) : TaskRunRepository {
    private fun runs(taskId: String) = firestore.collection("tasks").document(taskId).collection("runs")

    override fun record(run: TaskRun): TaskRun {
        runs(run.taskId).document(run.id).set(TaskRunDocuments.toFields(run, json)).get()
        return run
    }

    override fun findByTaskId(
        taskId: String,
        limit: Int,
    ): List<TaskRun> =
        runs(taskId)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .get()
            .documents
            .map { TaskRunDocuments.fromFields(it.data, json) }
}

/** Pure mapping between [TaskRun] and Firestore fields, unit-testable without Firestore. */
internal object TaskRunDocuments {
    private val deliveriesSerializer = ListSerializer(NotificationDelivery.serializer())

    fun toFields(
        run: TaskRun,
        json: Json,
    ): Map<String, Any?> {
        val startedAt = OffsetDateTime.parse(run.startedAtIso).toInstant()
        return mapOf(
            "id" to run.id,
            "taskId" to run.taskId,
            "taskName" to run.taskName,
            "taskType" to run.taskType.name,
            "trigger" to run.trigger.name,
            "status" to run.status.name,
            "error" to run.error,
            "outputJson" to run.outputJson,
            "deliveriesJson" to json.encodeToString(deliveriesSerializer, run.deliveries),
            "requestId" to run.requestId,
            "startedAtIso" to run.startedAtIso,
            "finishedAtIso" to run.finishedAtIso,
            "startedAt" to timestamp(startedAt),
            "expireAt" to timestamp(startedAt.plus(TASK_RUN_RETENTION)),
        )
    }

    fun fromFields(
        fields: Map<String, Any?>,
        json: Json,
    ): TaskRun =
        TaskRun(
            id = fields["id"] as String,
            taskId = fields["taskId"] as String,
            taskName = fields["taskName"] as String,
            taskType = TaskType.valueOf(fields["taskType"] as String),
            trigger = TaskRunTrigger.valueOf(fields["trigger"] as String),
            startedAtIso = fields["startedAtIso"] as String,
            finishedAtIso = fields["finishedAtIso"] as String,
            status = TaskStatus.valueOf(fields["status"] as String),
            error = fields["error"] as String?,
            outputJson = fields["outputJson"] as String?,
            requestId = fields["requestId"] as String?,
            deliveries = (fields["deliveriesJson"] as String?)?.let { json.decodeFromString(deliveriesSerializer, it) }.orEmpty(),
        )

    private fun timestamp(instant: Instant): Timestamp = Timestamp.ofTimeSecondsAndNanos(instant.epochSecond, instant.nano)
}
