package com.todo.desktop.domain.model;

import java.time.Duration;
import java.util.Objects;

public record ReminderPolicy(
        boolean enabled,
        Duration leadTime
) {

    public ReminderPolicy {
        Objects.requireNonNull(leadTime, "leadTime");
    }

    public static ReminderPolicy disabled() {
        return new ReminderPolicy(false, Duration.ZERO);
    }
}
