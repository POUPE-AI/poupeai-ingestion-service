package io.github.poupeai.ingestion.client;

import io.github.poupeai.ingestion.client.dto.CategorizationRequest;
import io.github.poupeai.ingestion.client.dto.CategorizationResponse;
import io.github.poupeai.ingestion.config.CoreFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "report-service",
        url = "${app.services.report-url}",
        configuration = CoreFeignConfig.class
)
public interface ReportServiceClient {
    @PostMapping("/api/internal/categorization/predict")
    CategorizationResponse predictCategories(@RequestBody CategorizationRequest request);
}
