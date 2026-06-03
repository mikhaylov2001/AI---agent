package com.niki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HhWebClientConfig {

    @Value("${hh.user-agent:NikiBot/2.0 (babaykin35@gmail.com)}")
    private String userAgent;

    @Bean
    WebClient hhApiWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.hh.ru")
                .defaultHeader("User-Agent", userAgent)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Bean
    WebClient hhOAuthWebClient() {
        return WebClient.builder()
                .baseUrl("https://hh.ru")
                .defaultHeader("User-Agent", userAgent)
                .build();
    }
}
