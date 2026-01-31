package io.github.poupeai.ingestion.client;

import io.github.poupeai.ingestion.config.CoreFeignConfig;
import io.github.poupeai.ingestion.dto.CategoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "core-service",
        url = "${app.services.core-url}",
        configuration = CoreFeignConfig.class
)
public interface CoreServiceClient {
    @GetMapping("/api/internal/categories")
    List<CategoryDTO> getCategories(@RequestParam("profileId") String profileId);
}
