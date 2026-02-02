package io.github.poupeai.ingestion.domain.event;

public record IngestionJobPayload(
        String jobId,
        String fileKey,
        ProfileInfo profile,
        AccountInfo bankAccount,
        String fallbackIncomeCategoryId,
        String fallbackExpenseCategoryId
) {
    public record ProfileInfo(String id, String name, String email) {}
    public record AccountInfo(String id, String name) {}
}
