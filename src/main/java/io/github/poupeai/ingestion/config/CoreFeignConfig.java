package io.github.poupeai.ingestion.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class CoreFeignConfig {
    @Value("${app.security.internal-api-key}")
    private String internalApiKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> template.header("x-api-key", internalApiKey);
    }
}
