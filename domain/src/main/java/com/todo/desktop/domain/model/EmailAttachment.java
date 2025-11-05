package com.todo.desktop.domain.model;

public record EmailAttachment(
        String id,
        String name,
        String contentType,
        int size,
        boolean isInline
) {
}
