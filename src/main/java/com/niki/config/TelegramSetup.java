package com.niki.config;

import com.niki.bot.NikiBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TelegramSetup {

    private final NikiBot nikiBot;
    private final TelegramBotsApi telegramBotsApi;

    @Value("${telegram.webhook.base-url:}")
    private String webhookBaseUrl;

    @Value("${telegram.webhook.path:/telegram/webhook}")
    private String webhookPath;

    @EventListener(ApplicationReadyEvent.class)
    public void connectTelegram() {
        if (StringUtils.hasText(webhookBaseUrl)) {
            enableWebhook();
        } else {
            enableLongPolling();
        }
    }

    private void enableWebhook() {
        String base = webhookBaseUrl.replaceAll("/$", "");
        String url = base + webhookPath;
        try {
            nikiBot.execute(SetWebhook.builder()
                    .url(url)
                    .maxConnections(40)
                    .build());
            log.info("Telegram WEBHOOK: {}", url);
        } catch (TelegramApiException e) {
            log.error("Webhook не установлен {}: {}", url, e.getMessage(), e);
        }
    }

    private void enableLongPolling() {
        try {
            telegramBotsApi.registerBot(nikiBot);
            log.info("Telegram LONG POLLING (локально)");
        } catch (TelegramApiException e) {
            log.error("Long polling не запущен: {}", e.getMessage(), e);
        }
    }
}
