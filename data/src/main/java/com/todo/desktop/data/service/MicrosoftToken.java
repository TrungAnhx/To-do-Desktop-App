package com.todo.desktop.data.service;

import java.time.Instant;

public record MicrosoftToken(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt.minusSeconds(60));
    }
}
