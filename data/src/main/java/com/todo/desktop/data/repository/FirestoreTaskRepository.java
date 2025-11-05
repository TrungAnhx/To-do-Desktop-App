package com.todo.desktop.data.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.common.base.Strings;
import com.todo.desktop.data.firebase.FirebaseFutures;
import com.todo.desktop.domain.model.Task;
import com.todo.desktop.domain.model.Task.TaskStatus;
import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;
import com.todo.desktop.domain.usecase.TaskService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FirestoreTaskRepository implements TaskService {

    private static final String USERS_COLLECTION = "users";
    private static final String TASKS_COLLECTION = "tasks";

    private final Firestore firestore;
    private final AuthService authService;

    public FirestoreTaskRepository(Firestore firestore, AuthService authService) {
        this.firestore = Objects.requireNonNull(firestore, "firestore");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    @Override
    public CompletableFuture<List<Task>> listTasks() {
        Optional<String> uid = currentUserId();
        if (uid.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        ApiFuture<QuerySnapshot> query = tasksCollection(uid.get())
                .orderBy("dueAt", Direction.ASCENDING)
                .get();
        return FirebaseFutures.toCompletable(query)
                .thenApply(snapshot -> {
                    List<Task> results = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Task task = mapToTask(document);
                        if (task != null) {
                            results.add(task);
                        }
                    }
                    return results;
                });
    }

    @Override
    public CompletableFuture<Task> saveTask(Task task) {
        Objects.requireNonNull(task, "task");
        String uid = currentUserId().orElseThrow(() -> new IllegalStateException("Người dùng chưa đăng nhập"));
        CollectionReference collection = tasksCollection(uid);
        String id = Strings.isNullOrEmpty(task.id()) ? collection.document().getId() : task.id();
        DocumentReference document = collection.document(id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("title", task.title());
        payload.put("description", task.description());
        payload.put("status", task.status().name());
        payload.put("flagged", task.flagged());
        if (task.dueAt() != null) {
            payload.put("dueAt", Timestamp.ofTimeSecondsAndNanos(task.dueAt().getEpochSecond(), task.dueAt().getNano()));
        } else {
            payload.put("dueAt", null);
        }

        ApiFuture<?> writeFuture = document.set(payload, SetOptions.merge());
        return FirebaseFutures.toCompletable(writeFuture)
                .thenApply(ignored -> new Task(
                        id,
                        task.title(),
                        task.description(),
                        task.dueAt(),
                        task.status(),
                        task.flagged()
                ));
    }

    @Override
    public CompletableFuture<Void> deleteTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        String uid = currentUserId().orElseThrow(() -> new IllegalStateException("Người dùng chưa đăng nhập"));
        DocumentReference document = tasksCollection(uid).document(taskId);
        return FirebaseFutures.toCompletable(document.delete()).thenApply(ignored -> null);
    }

    private Optional<String> currentUserId() {
        return authService.currentUser().map(UserProfile::uid);
    }

    private CollectionReference tasksCollection(String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(TASKS_COLLECTION);
    }

    private Task mapToTask(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            return null;
        }
        String id = snapshot.getString("id");
        String title = snapshot.getString("title");
        String description = snapshot.getString("description");
        Boolean flagged = snapshot.getBoolean("flagged");
        String statusRaw = snapshot.getString("status");
        Timestamp dueTimestamp = snapshot.getTimestamp("dueAt");

        Instant dueAt = toInstant(dueTimestamp);
        TaskStatus status = statusRaw != null ? parseStatus(statusRaw) : TaskStatus.TODO;

        return new Task(
                id != null ? id : snapshot.getId(),
                title != null ? title : "",
                description,
                dueAt,
                status,
                flagged != null && flagged
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private TaskStatus parseStatus(String statusRaw) {
        try {
            return TaskStatus.valueOf(statusRaw);
        } catch (IllegalArgumentException ex) {
            return TaskStatus.TODO;
        }
    }
}
