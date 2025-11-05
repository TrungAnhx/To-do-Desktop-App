package com.todo.desktop.data.repository;

import com.todo.desktop.data.graph.GraphMailClient;
import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.EmailService;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class GraphEmailRepository implements EmailService {

    private final GraphMailClient graphMailClient;

    public GraphEmailRepository(GraphMailClient graphMailClient) {
        this.graphMailClient = Objects.requireNonNull(graphMailClient, "graphMailClient");
    }

    @Override
    public CompletableFuture<List<EmailMessage>> listInbox() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<EmailMessage> fetchMessage(String messageId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public CompletableFuture<Void> sendMessage(EmailMessage draft, List<String> cc, List<String> bcc, List<Path> attachments) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
    }
}
