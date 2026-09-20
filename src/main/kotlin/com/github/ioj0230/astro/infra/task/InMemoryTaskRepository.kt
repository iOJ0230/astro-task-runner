package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [TaskRepository]. **Not used in production** —
 * `Application.module()` always wires [com.github.ioj0230.astro.infra.task.FirestoreTaskRepository]
 * instead. This exists purely so tests (`TaskRunnerSchedulingTest`,
 * `Application.testModule`) can exercise [TaskRunner][com.github.ioj0230.astro.core.task.TaskRunner]
 * and the route layer without live GCP credentials. Keep both this class
 * and its test-only usage — it is deliberately dead in prod, not
 * leftover. See CLAUDE.md "Known gaps" #1 and #4.
 */
class InMemoryTaskRepository : TaskRepository {
    private val storage = ConcurrentHashMap<String, Task>()

    override fun create(task: Task): Task {
        storage[task.id] = task
        return task
    }

    override fun findById(id: String): Task? = storage[id]

    override fun findAll(): List<Task> = storage.values.sortedBy { it.createdAtIso }

    override fun update(task: Task): Task {
        storage[task.id] = task
        return task
    }
}
