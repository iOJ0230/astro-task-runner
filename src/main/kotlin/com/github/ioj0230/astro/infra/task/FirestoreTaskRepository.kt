package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FirestoreTaskRepository(
    private val firestore: com.google.cloud.firestore.Firestore,
    private val json: Json,
) : TaskRepository {
    private val collection = firestore.collection("tasks")

    override fun create(task: Task): Task {
        collection.document(task.id).set(
            mapOf(
                "id" to task.id,
                "taskJson" to json.encodeToString(task),
                "updatedAtIso" to task.createdAtIso,
            ),
        ).get()
        return task
    }

    override fun findById(id: String): Task? {
        val snap = collection.document(id).get().get()
        if (!snap.exists()) return null
        val taskJson = snap.getString("taskJson") ?: return null
        return json.decodeFromString(Task.serializer(), taskJson)
    }

    override fun findAll(): List<Task> {
        val snaps = collection.get().get().documents
        return snaps.mapNotNull { doc ->
            doc.getString("taskJson")?.let { jsonStr ->
                json.decodeFromString(Task.serializer(), jsonStr)
            }
        }
    }

    override fun update(task: Task): Task {
        collection.document(task.id).set(
            mapOf(
                "id" to task.id,
                "taskJson" to json.encodeToString(Task.serializer(), task),
                "updatedAtIso" to (task.lastRunAtIso ?: task.createdAtIso),
            ),
        ).get()
        return task
    }
}
