package com.niki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AnthropicConfig {

    @Bean
    WebClient anthropicWebClient(@Value("${llm.anthropic.key:}") String apiKey) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(55))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey != null ? apiKey : "")
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
