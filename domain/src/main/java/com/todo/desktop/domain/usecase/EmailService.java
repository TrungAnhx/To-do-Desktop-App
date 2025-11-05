package com.todo.desktop.domain.usecase;

import com.todo.desktop.domain.model.EmailMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    CompletableFuture<List<EmailMessage>> listInbox();

    CompletableFuture<EmailMessage> fetchMessage(String messageId);

    CompletableFuture<Void> sendMessage(EmailMessage draft, List<String> cc, List<String> bcc, List<java.nio.file.Path> attachments);
}
