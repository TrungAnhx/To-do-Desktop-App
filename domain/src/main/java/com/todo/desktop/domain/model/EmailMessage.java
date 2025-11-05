package com.todo.desktop.domain.model;

import java.time.Instant;
import java.util.List;

public record EmailMessage(
        String id,
        String subject,
        String from,
        String fromEmail,
        List<String> toRecipients,
        String bodyPreview,
        String bodyContent,
        boolean isRead,
        boolean hasAttachments,
        Instant receivedDateTime,
        List<EmailAttachment> attachments
) {
}
