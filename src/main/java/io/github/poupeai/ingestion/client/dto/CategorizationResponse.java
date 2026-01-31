package io.github.poupeai.ingestion.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public record CategorizationResponse(
        Content content
) {
    public record Content(
            List<CategorizationItem> categorizations
    ) {}

    public record CategorizationItem(
            String description,
            @JsonProperty("category_id") String categoryId
    ) {}

    public List<CategorizationItem> getCategorizationsSafe() {
        if (content == null || content.categorizations == null) {
            return Collections.emptyList();
        }
        return content.categorizations;
    }
}
