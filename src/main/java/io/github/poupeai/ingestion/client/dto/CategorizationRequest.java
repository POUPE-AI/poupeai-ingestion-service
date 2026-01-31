package io.github.poupeai.ingestion.client.dto;

import io.github.poupeai.ingestion.dto.CategoryDTO;

import java.util.List;

public record CategorizationRequest(
        List<String> descriptions,
        List<CategoryDTO> userCategories
) { }
