package com.todo.desktop.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Task(
        String id,
        String title,
        String description,
        Instant dueAt,
        TaskStatus status,
        boolean flagged
) {

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
    }

    public enum TaskStatus {
        TODO,
        IN_PROGRESS,
        DONE
    }
}
