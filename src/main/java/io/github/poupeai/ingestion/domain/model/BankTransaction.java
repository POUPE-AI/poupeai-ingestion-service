package io.github.poupeai.ingestion.domain.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BankTransaction {
    private String fitId;
    private String bankCode;
    private LocalDateTime date;
    private BigDecimal amount;
    private String description;
    private String type;
    private String categoryId;
}
