package com.todo.desktop.data.mapper;

import com.microsoft.graph.models.Message;
import com.todo.desktop.domain.model.EmailMessage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public final class EmailMapper {

    private EmailMapper() {
    }

    public static EmailMessage toDomain(Message message) {
        List<String> recipients = Optional.ofNullable(message.toRecipients)
                .stream()
                .flatMap(List::stream)
                .map(recipient -> recipient.emailAddress.address)
                .toList();
        OffsetDateTime received = message.receivedDateTime;
        
        String fromName = Optional.ofNullable(message.from).map(addr -> addr.emailAddress.name).orElse("");
        String fromEmail = Optional.ofNullable(message.from).map(addr -> addr.emailAddress.address).orElse("");
        
        return new EmailMessage(
                message.id,
                message.subject != null ? message.subject : "(No subject)",
                fromName,
                fromEmail,
                recipients,
                Optional.ofNullable(message.bodyPreview).orElse(""),
                "", // bodyContent - will be loaded separately
                Boolean.TRUE.equals(message.isRead),
                message.hasAttachments != null && message.hasAttachments,
                received != null ? received.toInstant() : Instant.EPOCH,
                List.of() // attachments - will be loaded separately
        );
    }
}
