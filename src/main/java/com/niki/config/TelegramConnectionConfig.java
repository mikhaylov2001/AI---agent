package com.niki.config;

import com.niki.bot.NikiBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "telegram.delivery-mode", havingValue = "webhook", matchIfMissing = true)
public class TelegramConnectionConfig {

    private final NikiBot nikiBot;

    @Value("${telegram.webhook.public-url}")
    private String publicUrl;

    @Value("${telegram.webhook.path:/telegram/webhook}")
    private String webhookPath;

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        if (!StringUtils.hasText(publicUrl)) {
            log.error("telegram.webhook.public-url не задан — webhook не работает!");
            return;
        }
        String url = publicUrl.replaceAll("/$", "") + webhookPath;
        try {
            nikiBot.execute(SetWebhook.builder()
                    .url(url)
                    .maxConnections(40)
                    .dropPendingUpdates(false)
                    .build());
            log.info("Telegram WEBHOOK установлен: {}", url);
        } catch (TelegramApiException e) {
            log.error("Не удалось установить webhook {}: {}", url, e.getMessage(), e);
        }
    }
}
