package com.todo.desktop.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Deadline(
        String id,
        String taskId,
        Instant dueAt,
        String timeZoneId,
        ReminderPolicy reminderPolicy
) {

    public Deadline {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(timeZoneId, "timeZoneId");
    }
}
