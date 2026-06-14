package com.niki.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class StartupDiagnostics {

    @Value("${telegram.bot.token:}")
    private String telegramToken;

    @Value("${telegram.bot.username:}")
    private String telegramUsername;

    @Value("${telegram.delivery-mode:webhook}")
    private String deliveryMode;

    @Value("${telegram.webhook.public-url:}")
    private String webhookUrl;

    @Value("${llm.provider:claude}")
    private String llmProvider;

    @Value("${llm.api.key:}")
    private String llmKey;

    @Value("${llm.anthropic.key:}")
    private String claudeKey;

    @Value("${llm.api.base-url:}")
    private String llmBaseUrl;

    @Value("${llm.api.model:}")
    private String llmModel;

    @Value("${llm.api.fast-model:}")
    private String llmFastModel;

    @Value("${llm.anthropic.model:}")
    private String claudeModel;

    @Value("${llm.anthropic.fast-model:}")
    private String claudeFastModel;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        log.info("=== Niki Bot — проверка конфигурации ===");
        log.info("Telegram: {} ({})", maskToken(telegramToken), telegramUsername);
        log.info("Режим доставки: {}", deliveryMode);
        if ("webhook".equalsIgnoreCase(deliveryMode)) {
            log.info("Webhook URL: {}", StringUtils.hasText(webhookUrl) ? webhookUrl : "НЕ ЗАДАН — бот не получит сообщения!");
        }
        log.info("LLM: provider={}, groqKey={}, claudeKey={}",
                llmProvider,
                StringUtils.hasText(llmKey) ? "OK" : "нет",
                StringUtils.hasText(claudeKey) ? "OK" : "нет");
        if ("claude".equalsIgnoreCase(llmProvider) || "anthropic".equalsIgnoreCase(llmProvider)) {
            log.info("Claude models: smart={}, fast={}", claudeModel, claudeFastModel);
        } else {
            log.info("Groq models: smart={}, fast={}, base={}", llmModel, llmFastModel, llmBaseUrl);
        }
        log.info("БД: {}", StringUtils.hasText(dbUrl) ? maskJdbc(dbUrl) : "НЕ ЗАДАНА");
        if (!StringUtils.hasText(telegramToken)) {
            log.error("TELEGRAM_BOT_TOKEN не задан!");
        }
        boolean claudeMode = "claude".equalsIgnoreCase(llmProvider) || "anthropic".equalsIgnoreCase(llmProvider);
        if (claudeMode && !StringUtils.hasText(claudeKey)) {
            log.warn("CLAUDE_API_KEY не задан — диалог не работает.");
        }
        if (!claudeMode && !StringUtils.hasText(llmKey)) {
            log.warn("GROQ_API_KEY не задан — диалог и письма не работают.");
        }
        if ("webhook".equalsIgnoreCase(deliveryMode) && !StringUtils.hasText(webhookUrl)) {
            log.error("Задай TELEGRAM_WEBHOOK_URL или RENDER_EXTERNAL_URL на Render.");
        }
        log.info("========================================");
    }

    private static String maskToken(String token) {
        if (!StringUtils.hasText(token) || token.length() < 10) {
            return "не задан";
        }
        return token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }

    private static String maskJdbc(String url) {
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
