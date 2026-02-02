package io.github.poupeai.ingestion.domain.event;

public record NotificationRecipient(
        String userId,
        String email,
        String name
) { }