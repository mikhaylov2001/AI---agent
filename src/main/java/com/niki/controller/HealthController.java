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

    @Value("${llm.api.key:}")
    private String llmKey;

    @Value("${llm.provider:perplexity}")
    private String llmProvider;

    @Value("${llm.api.model:}")
    private String llmModel;

    @Value("${telegram.delivery-mode:webhook}")
    private String deliveryMode;

    @Value("${telegram.webhook.public-url:}")
    private String webhookUrl;

    @GetMapping({"/health", "/hh/health"})
    public String health() {
        return "ok";
    }

    @GetMapping("/health/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean llmOk = StringUtils.hasText(llmKey);
        body.put("status", allRequiredPresent() ? "ready" : "misconfigured");
        body.put("llm", llmOk);
        body.put("llmProvider", llmProvider);
        body.put("llmModel", llmModel);
        body.put("openai", llmOk); // обратная совместимость
        body.put("telegram", StringUtils.hasText(telegramToken));
        body.put("deliveryMode", deliveryMode);
        body.put("webhookUrl", StringUtils.hasText(webhookUrl) ? webhookUrl : null);
        return body;
    }

    private boolean allRequiredPresent() {
        if (!StringUtils.hasText(telegramToken) || !StringUtils.hasText(llmKey)) {
            return false;
        }
        if ("webhook".equalsIgnoreCase(deliveryMode)) {
            return StringUtils.hasText(webhookUrl);
        }
        return true;
    }
}
