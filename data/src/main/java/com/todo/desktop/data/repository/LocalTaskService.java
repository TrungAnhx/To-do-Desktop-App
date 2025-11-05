package com.todo.desktop.data.repository;

import com.todo.desktop.domain.model.Task;
import com.todo.desktop.domain.usecase.TaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalTaskService implements TaskService {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<Task>> listTasks() {
        return CompletableFuture.completedFuture(new ArrayList<>(tasks.values()));
    }

    @Override
    public CompletableFuture<Task> saveTask(Task task) {
        String id = task.id() == null || task.id().isBlank() ? UUID.randomUUID().toString() : task.id();
        Task normalized = new Task(id, task.title(), task.description(), task.dueAt(), task.status(), task.flagged());
        tasks.put(id, normalized);
        return CompletableFuture.completedFuture(normalized);
    }

    @Override
    public CompletableFuture<Void> deleteTask(String taskId) {
        tasks.remove(taskId);
        return CompletableFuture.completedFuture(null);
    }
}
