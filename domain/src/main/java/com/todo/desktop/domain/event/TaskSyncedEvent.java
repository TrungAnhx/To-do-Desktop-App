package com.todo.desktop.domain.event;

import com.todo.desktop.domain.model.Task;

public record TaskSyncedEvent(Task task) implements DomainEvent {
}
