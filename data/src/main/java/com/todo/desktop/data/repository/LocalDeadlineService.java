package com.todo.desktop.data.repository;

import com.todo.desktop.domain.model.Deadline;
import com.todo.desktop.domain.usecase.DeadlineService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalDeadlineService implements DeadlineService {

    private final Map<String, Deadline> deadlines = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<Deadline>> listDeadlines() {
        return CompletableFuture.completedFuture(new ArrayList<>(deadlines.values()));
    }

    @Override
    public CompletableFuture<Deadline> saveDeadline(Deadline deadline) {
        String id = deadline.id() == null || deadline.id().isBlank() ? UUID.randomUUID().toString() : deadline.id();
        Deadline normalized = new Deadline(id, deadline.taskId(), deadline.dueAt(), deadline.timeZoneId(), deadline.reminderPolicy());
        deadlines.put(id, normalized);
        return CompletableFuture.completedFuture(normalized);
    }

    @Override
    public CompletableFuture<Void> deleteDeadline(String deadlineId) {
        deadlines.remove(deadlineId);
        return CompletableFuture.completedFuture(null);
    }
}
