package com.todo.desktop.domain.usecase;

import com.todo.desktop.domain.model.Task;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TaskService {

    CompletableFuture<List<Task>> listTasks();

    CompletableFuture<Task> saveTask(Task task);

    CompletableFuture<Void> deleteTask(String taskId);
}
