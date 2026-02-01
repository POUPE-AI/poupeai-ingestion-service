package io.github.poupeai.ingestion.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIngestionJobRequest {
    private String status;
    private String summary;
    private String errorDetails;
}
