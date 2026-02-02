package io.github.poupeai.ingestion.domain.event;

import java.time.OffsetDateTime;

public record IngestionEvent(
        String messageId,
        OffsetDateTime timestamp,
        String triggerType,
        String eventType,
        IngestionJobPayload payload
) { }
