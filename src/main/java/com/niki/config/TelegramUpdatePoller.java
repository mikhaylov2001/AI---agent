package com.niki.config;

import com.niki.bot.NikiBot;
import com.niki.service.TelegramUpdateDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Забирает сообщения из Telegram каждые 500 ms.
 * Надёжнее long-polling потока и webhook на бесплатном Render.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "telegram.delivery-mode", havingValue = "polling")
public class TelegramUpdatePoller {

    private final NikiBot nikiBot;
    private final TelegramUpdateDispatcher updateDispatcher;
    private volatile int offset;

    @EventListener(ApplicationReadyEvent.class)
    public void prepare() {
        try {
            nikiBot.execute(DeleteWebhook.builder().dropPendingUpdates(false).build());
            log.info("Telegram polling активен (offset={}, webhook отключён)", offset);
        } catch (TelegramApiException e) {
            log.warn("DeleteWebhook: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 500)
    public void poll() {
        try {
            GetUpdates request = new GetUpdates();
            request.setTimeout(0);
            request.setOffset(offset);
            request.setLimit(100);

            List<Update> updates = nikiBot.execute(request);
            if (updates == null || updates.isEmpty()) {
                return;
            }
            for (Update update : updates) {
                updateDispatcher.dispatch(update);
                offset = update.getUpdateId() + 1;
            }
            log.info("Обработано сообщений: {}", updates.size());
        } catch (TelegramApiException e) {
            log.error("Ошибка getUpdates: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка polling: {}", e.getMessage(), e);
        }
    }
}
