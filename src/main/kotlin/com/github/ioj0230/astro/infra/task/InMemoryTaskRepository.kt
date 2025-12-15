package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.task.Task
import com.github.ioj0230.astro.core.task.TaskRepository
import java.util.concurrent.ConcurrentHashMap

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
