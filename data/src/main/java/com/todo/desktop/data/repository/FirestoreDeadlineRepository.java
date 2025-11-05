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
import com.todo.desktop.domain.model.Deadline;
import com.todo.desktop.domain.model.ReminderPolicy;
import com.todo.desktop.domain.model.UserProfile;
import com.todo.desktop.domain.usecase.AuthService;
import com.todo.desktop.domain.usecase.DeadlineService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FirestoreDeadlineRepository implements DeadlineService {

    private static final String USERS_COLLECTION = "users";
    private static final String DEADLINES_COLLECTION = "deadlines";

    private final Firestore firestore;
    private final AuthService authService;

    public FirestoreDeadlineRepository(Firestore firestore, AuthService authService) {
        this.firestore = Objects.requireNonNull(firestore, "firestore");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    @Override
    public CompletableFuture<List<Deadline>> listDeadlines() {
        Optional<String> uid = currentUserId();
        if (uid.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        ApiFuture<QuerySnapshot> query = deadlinesCollection(uid.get())
                .orderBy("dueAt", Direction.ASCENDING)
                .get();
        return FirebaseFutures.toCompletable(query)
                .thenApply(snapshot -> {
                    List<Deadline> results = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Deadline deadline = mapToDeadline(document);
                        if (deadline != null) {
                            results.add(deadline);
                        }
                    }
                    return results;
                });
    }

    @Override
    public CompletableFuture<Deadline> saveDeadline(Deadline deadline) {
        Objects.requireNonNull(deadline, "deadline");
        String uid = currentUserId().orElseThrow(() -> new IllegalStateException("Người dùng chưa đăng nhập"));
        CollectionReference collection = deadlinesCollection(uid);
        String id = Strings.isNullOrEmpty(deadline.id()) ? collection.document().getId() : deadline.id();
        DocumentReference document = collection.document(id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("taskId", deadline.taskId());
        payload.put("timeZoneId", deadline.timeZoneId());
        payload.put("dueAt", Timestamp.ofTimeSecondsAndNanos(deadline.dueAt().getEpochSecond(), deadline.dueAt().getNano()));

        ReminderPolicy policy = deadline.reminderPolicy();
        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put("enabled", policy.enabled());
        policyMap.put("leadMinutes", policy.leadTime().toMinutes());
        payload.put("reminderPolicy", policyMap);

        ApiFuture<?> writeFuture = document.set(payload, SetOptions.merge());
        return FirebaseFutures.toCompletable(writeFuture)
                .thenApply(ignored -> new Deadline(
                        id,
                        deadline.taskId(),
                        deadline.dueAt(),
                        deadline.timeZoneId(),
                        policy
                ));
    }

    @Override
    public CompletableFuture<Void> deleteDeadline(String deadlineId) {
        Objects.requireNonNull(deadlineId, "deadlineId");
        String uid = currentUserId().orElseThrow(() -> new IllegalStateException("Người dùng chưa đăng nhập"));
        DocumentReference document = deadlinesCollection(uid).document(deadlineId);
        return FirebaseFutures.toCompletable(document.delete()).thenApply(ignored -> null);
    }

    private Optional<String> currentUserId() {
        return authService.currentUser().map(UserProfile::uid);
    }

    private CollectionReference deadlinesCollection(String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(DEADLINES_COLLECTION);
    }

    private Deadline mapToDeadline(DocumentSnapshot document) {
        if (!document.exists()) {
            return null;
        }
        String id = document.getString("id");
        String taskId = document.getString("taskId");
        String timeZoneId = document.getString("timeZoneId");
        Timestamp dueTimestamp = document.getTimestamp("dueAt");

        if (dueTimestamp == null || taskId == null || timeZoneId == null) {
            return null;
        }

        ReminderPolicy policy = parsePolicy(document.get("reminderPolicy"));

        return new Deadline(
                id != null ? id : document.getId(),
                taskId,
                toInstant(dueTimestamp),
                timeZoneId,
                policy
        );
    }

    @SuppressWarnings("unchecked")
    private ReminderPolicy parsePolicy(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object enabledRaw = map.get("enabled");
            Object leadRaw = map.get("leadMinutes");

            boolean enabled = enabledRaw instanceof Boolean b && b;
            long minutes = leadRaw instanceof Number number ? number.longValue() : 0L;
            return new ReminderPolicy(enabled, Duration.ofMinutes(minutes));
        }
        return ReminderPolicy.disabled();
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return java.time.Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
