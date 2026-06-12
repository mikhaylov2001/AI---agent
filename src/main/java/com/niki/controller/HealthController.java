package com.niki.controller;

import com.niki.service.HhAppTokenService;
import com.niki.service.HhService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HhService hhService;
    private final HhAppTokenService hhAppTokenService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${telegram.bot.token:}")
    private String telegramToken;

    @Value("${llm.api.key:}")
    private String llmKey;

    @Value("${llm.provider:groq}")
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

    @GetMapping("/health/hh")
    public Map<String, Object> hhHealth() {
        HhService.VacancySearchResult r = hhService.searchVacancies("Java backend", "", 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hhSearch", r.error() == null);
        body.put("found", r.vacancies().size());
        body.put("error", r.error());
        body.put("hhAppConfigured", hhAppTokenService.isConfigured());
        return body;
    }

    @GetMapping("/health/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean llmOk = StringUtils.hasText(llmKey);
        boolean dbOk = checkDb();
        body.put("status", allRequiredPresent() && dbOk ? "ready" : "misconfigured");
        body.put("db", dbOk);
        body.put("llm", llmOk);
        body.put("llmProvider", llmProvider);
        body.put("llmModel", llmModel);
        body.put("openai", llmOk);
        body.put("telegram", StringUtils.hasText(telegramToken));
        body.put("deliveryMode", deliveryMode);
        body.put("webhookUrl", StringUtils.hasText(webhookUrl) ? webhookUrl : null);
        body.put("hhAppConfigured", hhAppTokenService.isConfigured());
        body.put("version", "2.0.9");
        return body;
    }

    private boolean checkDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
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
