package io.github.poupeai.ingestion.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationEvent(
        String messageId,
        OffsetDateTime timestamp,
        String triggerType,
        String eventType,
        NotificationRecipient recipient,
        NotificationPayload payload
) {
    public static NotificationEvent create(String eventType, NotificationRecipient recipient, NotificationPayload payload) {
        return new NotificationEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                "async_process_completion",
                eventType,
                recipient,
                payload
        );
    }
}