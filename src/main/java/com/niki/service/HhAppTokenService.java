package com.niki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Токен приложения HH (client_credentials) — для поиска вакансий без OAuth пользователя.
 */
@Service
@Slf4j
public class HhAppTokenService {

    private final WebClient hhClient = WebClient.builder()
            .baseUrl("https://hh.ru")
            .build();

    @Value("${hh.client.id:}")
    private String clientId;

    @Value("${hh.client.secret:}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    @SuppressWarnings("unchecked")
    public String getAppToken() {
        if (!isConfigured()) {
            return null;
        }
        if (cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(300))) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(300))) {
                return cachedToken;
            }
            try {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("grant_type", "client_credentials");
                body.add("client_id", clientId);
                body.add("client_secret", clientSecret);

                Map<String, Object> response = hhClient.post()
                        .uri("/oauth/token")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .body(BodyInserters.fromFormData(body))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response == null || response.get("access_token") == null) {
                    log.warn("HH app token: пустой ответ");
                    return null;
                }
                cachedToken = (String) response.get("access_token");
                Number expiresIn = (Number) response.getOrDefault("expires_in", 3600);
                tokenExpiresAt = Instant.now().plusSeconds(expiresIn.longValue());
                log.info("HH app token получен, expires_in={}s", expiresIn);
                return cachedToken;
            } catch (Exception e) {
                log.error("HH app token ошибка: {}", e.getMessage());
                return null;
            }
        }
    }
}
