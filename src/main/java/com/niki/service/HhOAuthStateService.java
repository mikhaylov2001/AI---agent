package com.niki.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class HhOAuthStateService {

    private static final long TTL_SECONDS = 3600;

    @Value("${hh.client.secret:}")
    private String clientSecret;

    @Value("${niki.state-secret:${HH_CLIENT_SECRET:change-me}}")
    private String stateSecret;

    public String encode(Long telegramId) {
        long expiry = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = telegramId + ":" + expiry;
        String sig = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + sig).getBytes(StandardCharsets.UTF_8));
    }

    public Long decode(String state) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("Пустой state");
        }
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Неверный state");
        }
        long telegramId = Long.parseLong(parts[0]);
        long expiry = Long.parseLong(parts[1]);
        String sig = parts[2];
        if (Instant.now().getEpochSecond() > expiry) {
            throw new IllegalArgumentException("State истёк");
        }
        String expected = sign(telegramId + ":" + expiry);
        if (!expected.equals(sig)) {
            throw new IllegalArgumentException("Неверная подпись state");
        }
        return telegramId;
    }

    private String sign(String payload) {
        try {
            String secret = StringUtils.hasText(stateSecret) ? stateSecret : clientSecret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка подписи state", e);
        }
    }
}
