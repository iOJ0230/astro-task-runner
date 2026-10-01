package com.github.ioj0230.astro.infra.task

import com.github.ioj0230.astro.core.task.TaskRun
import com.github.ioj0230.astro.core.task.TaskRunRepository
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [TaskRunRepository] for tests and `testModule()` only — same
 * role as [InMemoryTaskRepository]. Production uses
 * [FirestoreTaskRunRepository].
 */
class InMemoryTaskRunRepository : TaskRunRepository {
    private val runs = CopyOnWriteArrayList<TaskRun>()

    override fun record(run: TaskRun): TaskRun {
        runs += run
        return run
    }

    // Insertion order is run order, so "most recent first" is just reversed.
    override fun findByTaskId(
        taskId: String,
        limit: Int,
    ): List<TaskRun> = runs.filter { it.taskId == taskId }.asReversed().take(limit)
}
