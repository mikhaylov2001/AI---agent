package com.niki.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${telegram.bot.token:}")
    private String telegramToken;

    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${telegram.delivery-mode:webhook}")
    private String deliveryMode;

    @Value("${telegram.webhook.public-url:}")
    private String webhookUrl;

    @GetMapping({"/health", "/hh/health"})
    public String health() {
        return "ok";
    }

    /** Статус без секретов — для проверки после деплоя. */
    @GetMapping("/health/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allRequiredPresent() ? "ready" : "misconfigured");
        body.put("telegram", StringUtils.hasText(telegramToken));
        body.put("openai", StringUtils.hasText(openAiKey));
        body.put("deliveryMode", deliveryMode);
        body.put("webhookUrl", StringUtils.hasText(webhookUrl) ? webhookUrl : null);
        return body;
    }

    private boolean allRequiredPresent() {
        if (!StringUtils.hasText(telegramToken) || !StringUtils.hasText(openAiKey)) {
            return false;
        }
        if ("webhook".equalsIgnoreCase(deliveryMode)) {
            return StringUtils.hasText(webhookUrl);
        }
        return true;
    }
}
