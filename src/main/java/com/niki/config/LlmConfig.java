package com.niki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LlmConfig {

    @Bean
    public WebClient llmWebClient(
            @Value("${llm.api.base-url}") String baseUrl,
            @Value("${llm.api.key:}") String apiKey) {
        String normalized = baseUrl.replaceAll("/+$", "");
        return WebClient.builder()
                .baseUrl(normalized)
                .defaultHeaders(headers -> {
                    if (StringUtils.hasText(apiKey)) {
                        headers.setBearerAuth(apiKey);
                    }
                    headers.set("Content-Type", "application/json");
                })
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
