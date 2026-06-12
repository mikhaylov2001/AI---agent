package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhOAuthService {

    private final UserRepository userRepository;
    private final HhOAuthStateService stateService;

    @Qualifier("hhOAuthWebClient")
    private final WebClient hhOAuthWebClient;

    @Value("${hh.client.id:}")
    private String clientId;

    @Value("${hh.client.secret:}")
    private String clientSecret;

    @Value("${hh.redirect.uri}")
    private String redirectUri;

    @Value("${telegram.webhook.public-url:}")
    private String publicBaseUrl;

    /** Ссылка для кнопки в Telegram — через наш /hh/authorize (без _ в URL). */
    public String buildTelegramConnectUrl(Long telegramId) {
        if (!StringUtils.hasText(clientId)) {
            return "HH_CLIENT_ID не настроен. Зарегистрируй приложение на dev.hh.ru.";
        }
        String state = stateService.encode(telegramId);
        if (!StringUtils.hasText(publicBaseUrl)) {
            return buildHhAuthorizeUrlWithState(state);
        }
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/hh/authorize?s="
                + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    /** Прямой URL на hh.ru — %5F вместо _ в именах параметров. */
    public String buildHhAuthorizeUrlWithState(String state) {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException("HH_CLIENT_ID не настроен");
        }
        return "https://hh.ru/oauth/authorize"
                + "?response%5Ftype=code"
                + "&client%5Fid=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    public String buildHhAuthorizeUrl(Long telegramId) {
        return buildHhAuthorizeUrlWithState(stateService.encode(telegramId));
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

        Map<String, Object> response = hhOAuthWebClient.post()
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
        if (!StringUtils.hasText(user.getHhRefreshToken())) {
            clearTokens(user);
            throw new HhAuthException("Нет refresh token — нужна повторная авторизация");
        }
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new HhAuthException("HH_CLIENT_ID или HH_CLIENT_SECRET не настроены на сервере");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", user.getHhRefreshToken());

        try {
            Map<String, Object> response = hhOAuthWebClient.post()
                    .uri("/oauth/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(BodyInserters.fromFormData(body))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !StringUtils.hasText((String) response.get("access_token"))) {
                clearTokens(user);
                throw new HhAuthException("Пустой ответ при обновлении HH-токена");
            }

            user.setHhAccessToken((String) response.get("access_token"));
            if (response.get("refresh_token") != null) {
                user.setHhRefreshToken((String) response.get("refresh_token"));
            }
            user.setHhTokenExpiresAt(LocalDateTime.now().plusSeconds(((Number) response.get("expires_in")).longValue()));
            userRepository.save(user);
            log.info("HH токен обновлён для {}", user.getTelegramId());
        } catch (WebClientResponseException e) {
            log.error("HH refresh {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            clearTokens(user);
            throw new HhAuthException("Не удалось обновить HH-сессию", e);
        } catch (HhAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("HH refresh: {}", e.getMessage(), e);
            clearTokens(user);
            throw new HhAuthException("Не удалось обновить HH-сессию", e);
        }
    }

    /** Принудительное обновление — после 401 от API. */
    @Transactional
    public void forceRefreshAccessToken(User user) {
        refreshAccessToken(user);
    }

    public String getValidToken(User user) {
        if (!isConnected(user)) {
            throw new HhAuthException("HH не подключён");
        }
        if (needsRefresh(user)) {
            refreshAccessToken(user);
            user = userRepository.findByTelegramId(user.getTelegramId()).orElseThrow();
        }
        return user.getHhAccessToken();
    }

    @Transactional
    public void clearTokens(User user) {
        user.setHhAccessToken(null);
        user.setHhRefreshToken(null);
        user.setHhTokenExpiresAt(null);
        userRepository.save(user);
    }

    public boolean isConnected(User user) {
        return StringUtils.hasText(user.getHhAccessToken())
                && StringUtils.hasText(user.getHhRefreshToken());
    }

    private boolean needsRefresh(User user) {
        return user.getHhTokenExpiresAt() == null
                || user.getHhTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5));
    }
}
