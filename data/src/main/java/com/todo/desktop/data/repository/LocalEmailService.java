package com.todo.desktop.data.repository;

import com.todo.desktop.domain.model.EmailMessage;
import com.todo.desktop.domain.usecase.EmailService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LocalEmailService implements EmailService {

    private final List<EmailMessage> inbox = new CopyOnWriteArrayList<>();

    public LocalEmailService() {
        inbox.add(new EmailMessage(
                UUID.randomUUID().toString(),
                "Chào mừng đến với EveryDay Planner",
                "System",
                "system@planner.local",
                List.of("you@example.com"),
                "Hãy kết nối Outlook để đồng bộ email.",
                "",
                true,
                false,
                Instant.now(),
                List.of()
        ));
    }

    @Override
    public CompletableFuture<List<EmailMessage>> listInbox() {
        return CompletableFuture.completedFuture(new ArrayList<>(inbox));
    }

    @Override
    public CompletableFuture<EmailMessage> fetchMessage(String messageId) {
        return CompletableFuture.completedFuture(
                inbox.stream().filter(mail -> mail.id().equals(messageId)).findFirst().orElse(null)
        );
    }

    @Override
    public CompletableFuture<Void> sendMessage(EmailMessage draft, List<String> cc, List<String> bcc, List<Path> attachments) {
        EmailMessage stored = new EmailMessage(
                UUID.randomUUID().toString(),
                draft.subject(),
                draft.from(),
                draft.fromEmail(),
                draft.toRecipients(),
                draft.bodyPreview(),
                draft.bodyContent(),
                false,
                attachments != null && !attachments.isEmpty(),
                Instant.now(),
                List.of()
        );
        inbox.add(0, stored);
        return CompletableFuture.completedFuture(null);
    }
}
