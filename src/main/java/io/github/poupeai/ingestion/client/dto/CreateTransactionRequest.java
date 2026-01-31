package io.github.poupeai.ingestion.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        UUID profileId,
        UUID bankAccountId,
        String description,
        BigDecimal amount,
        TransactionType type,
        LocalDate date,
        UUID categoryId,
        String originalStatementId
) { }
