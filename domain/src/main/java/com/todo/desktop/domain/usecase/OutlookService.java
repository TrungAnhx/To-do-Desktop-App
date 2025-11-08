package com.todo.desktop.domain.usecase;

import com.todo.desktop.domain.model.EmailMessage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OutlookService {
    CompletableFuture<Void> connectOutlook();
    CompletableFuture<Void> sendEmail(String toEmail, String subject, String body);
    CompletableFuture<Void> sendEmailWithAttachments(String toEmail, String subject, String body, List<File> attachments);
    CompletableFuture<List<EmailMessage>> getInboxMessages(int top);
    CompletableFuture<List<EmailMessage>> getInboxMessages(int top, int skip);
    CompletableFuture<EmailMessage> getMessageById(String messageId);
    CompletableFuture<byte[]> downloadAttachment(String messageId, String attachmentId);
    boolean isConnected();
    void disconnect();
}
