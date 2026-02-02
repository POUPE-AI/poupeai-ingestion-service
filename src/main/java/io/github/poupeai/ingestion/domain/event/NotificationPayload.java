package io.github.poupeai.ingestion.domain.event;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationPayload(
        String status,
        String fileName,
        String accountName,
        String errorCode,
        String errorMessage
) {
    public static NotificationPayload success(String fileName, String accountName) {
        return new NotificationPayload("SUCCESS", fileName, accountName, null, null);
    }

    public static NotificationPayload error(String fileName, String accountName, String errorCode, String errorMessage) {
        return new NotificationPayload("FAILED", fileName, accountName, errorCode, errorMessage);
    }
}