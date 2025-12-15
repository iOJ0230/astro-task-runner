package com.github.ioj0230.astro.core.task

interface TaskRepository {
    fun create(task: Task): Task

    fun findById(id: String): Task?

    fun findAll(): List<Task>

    fun update(task: Task): Task
}
