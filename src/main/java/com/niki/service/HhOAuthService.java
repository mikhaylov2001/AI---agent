package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhOAuthService {

    private final UserRepository userRepository;
    private final WebClient hhClient = WebClient.builder()
            .baseUrl("https://hh.ru")
            .defaultHeader("User-Agent", "NikiBot/1.0 (nikibot@gmail.com)")
            .build();

    @Value("${hh.client.id:}")
    private String clientId;

    @Value("${hh.client.secret:}")
    private String clientSecret;

    @Value("${hh.redirect.uri}")
    private String redirectUri;

    public String buildAuthUrl(Long telegramId) {
        if (!StringUtils.hasText(clientId)) {
            return "HH_CLIENT_ID не настроен. Зарегистрируй приложение на dev.hh.ru.";
        }
        return String.format(
                "https://hh.ru/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%d",
                clientId, redirectUri, telegramId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void exchangeCodeForTokens(Long telegramId, String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        Map<String, Object> response = hhClient.post()
                .uri("/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Пустой ответ от HH OAuth");
        }

        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + telegramId));

        user.setHhAccessToken((String) response.get("access_token"));
        user.setHhRefreshToken((String) response.get("refresh_token"));
        user.setHhTokenExpiresAt(LocalDateTime.now().plusSeconds(((Number) response.get("expires_in")).longValue()));
        userRepository.save(user);
        log.info("HH токен сохранён для {}", telegramId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void refreshAccessToken(User user) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", user.getHhRefreshToken());

        Map<String, Object> response = hhClient.post()
                .uri("/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Ошибка обновления токена");
        }

        user.setHhAccessToken((String) response.get("access_token"));
        user.setHhRefreshToken((String) response.get("refresh_token"));
        user.setHhTokenExpiresAt(LocalDateTime.now().plusSeconds(((Number) response.get("expires_in")).longValue()));
        userRepository.save(user);
    }

    public String getValidToken(User user) {
        if (user.getHhTokenExpiresAt() == null ||
                user.getHhTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            refreshAccessToken(user);
            user = userRepository.findByTelegramId(user.getTelegramId()).orElseThrow();
        }
        return user.getHhAccessToken();
    }

    public boolean isConnected(User user) {
        return user.getHhAccessToken() != null && !user.getHhAccessToken().isBlank();
    }
}
