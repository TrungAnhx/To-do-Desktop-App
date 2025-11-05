package com.todo.desktop.domain.model;

import java.net.URI;
import java.util.Objects;

public record UserProfile(
        String uid,
        String displayName,
        String email,
        URI avatarUri
) {

    public UserProfile {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(email, "email");
    }
}
