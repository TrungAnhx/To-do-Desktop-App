package com.todo.desktop.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalAuthService implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final AtomicReference<UserProfile> current = new AtomicReference<>();

    public LocalAuthService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public Optional<UserProfile> currentUser() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public CompletableFuture<UserProfile> signInWithPassword(String email, String password) {
        if (firebaseAuth != null) {
            throw new UnsupportedOperationException("Sign-in qua Firebase Auth Client SDK sẽ được tích hợp sau");
        }
        UserProfile profile = new UserProfile(UUID.randomUUID().toString(), email, email, (URI) null);
        current.set(profile);
        return CompletableFuture.completedFuture(profile);
    }

    @Override
    public CompletableFuture<UserProfile> register(String email, String password, String displayName) {
        if (firebaseAuth != null) {
            throw new UnsupportedOperationException("Đăng ký người dùng Firebase sẽ được triển khai ở bước kế tiếp");
        }
        UserProfile profile = new UserProfile(UUID.randomUUID().toString(), displayName, email, (URI) null);
        current.set(profile);
        return CompletableFuture.completedFuture(profile);
    }

    @Override
    public CompletableFuture<Void> signOut() {
        current.set(null);
        return CompletableFuture.completedFuture(null);
    }
}
