package com.niki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiConfig {

    @Bean
    public WebClient openAiWebClient(
            @Value("${openai.api.base-url}") String baseUrl,
            @Value("${openai.api.key:}") String apiKey) {
        String normalized = baseUrl.replaceAll("/+$", "");
        return WebClient.builder()
                .baseUrl(normalized)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
