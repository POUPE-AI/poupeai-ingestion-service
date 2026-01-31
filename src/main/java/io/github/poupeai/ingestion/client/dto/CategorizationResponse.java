package io.github.poupeai.ingestion.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CategorizationResponse(
        List<CategorizationItem> categorizations
) {
    public record CategorizationItem(
            String description,
            @JsonProperty("category_id") String categoryId
    ) {}
}
