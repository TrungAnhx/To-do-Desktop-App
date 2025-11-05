package com.todo.desktop.domain.usecase;

import com.todo.desktop.domain.model.UserProfile;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AuthService {

    Optional<UserProfile> currentUser();

    CompletableFuture<UserProfile> signInWithPassword(String email, String password);

    CompletableFuture<UserProfile> register(String email, String password, String displayName);

    CompletableFuture<Void> signOut();
}
