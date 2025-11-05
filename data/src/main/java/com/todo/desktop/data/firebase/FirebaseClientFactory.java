package com.todo.desktop.data.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FirebaseClientFactory {

    private final AtomicReference<FirebaseApp> appRef = new AtomicReference<>();

    public FirebaseApp initializeIfNeeded(InputStream serviceAccountStream, String projectId, String storageBucket) throws IOException {
        FirebaseApp existing = appRef.get();
        if (existing != null) {
            return existing;
        }
        Objects.requireNonNull(serviceAccountStream, "serviceAccountStream");
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream));
        if (storageBucket != null && !storageBucket.isBlank()) {
            builder.setStorageBucket(storageBucket);
        }
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        FirebaseOptions options = builder.build();
        FirebaseApp created = FirebaseApp.initializeApp(options);
        appRef.set(created);
        return created;
    }
}
