package io.github.poupeai.ingestion.domain.event;

public record IngestionJobPayload(
        String jobId,
        String fileKey,
        String profileId,
        String bankAccountId,
        String fallbackIncomeCategoryId,
        String fallbackExpenseCategoryId
) { }
