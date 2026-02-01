package io.github.poupeai.ingestion.client;

import io.github.poupeai.ingestion.client.dto.CreateTransactionRequest;
import io.github.poupeai.ingestion.client.dto.UpdateIngestionJobRequest;
import io.github.poupeai.ingestion.config.CoreFeignConfig;
import io.github.poupeai.ingestion.dto.CategoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/api/internal/transactions/batch")
    void createTransactionsBatch(@RequestBody List<CreateTransactionRequest> transactions);

    @PatchMapping("/api/internal/ingestion-jobs/{id}")
    void updateStatus(@PathVariable("id") String id, @RequestBody UpdateIngestionJobRequest request);
}
