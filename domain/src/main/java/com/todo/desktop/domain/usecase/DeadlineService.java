package com.todo.desktop.domain.usecase;

import com.todo.desktop.domain.model.Deadline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DeadlineService {

    CompletableFuture<List<Deadline>> listDeadlines();

    CompletableFuture<Deadline> saveDeadline(Deadline deadline);

    CompletableFuture<Void> deleteDeadline(String deadlineId);
}
