package com.niki.service;

import com.niki.bot.NikiBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.concurrent.Executor;

/**
 * Webhook на Render должен ответить быстро; LLM/HH обрабатываем в фоне.
 */
@Service
@Slf4j
public class TelegramUpdateDispatcher {

    private final NikiBot nikiBot;
    private final Executor telegramUpdateExecutor;

    public TelegramUpdateDispatcher(NikiBot nikiBot,
                                    @Qualifier("telegramUpdateExecutor") Executor telegramUpdateExecutor) {
        this.nikiBot = nikiBot;
        this.telegramUpdateExecutor = telegramUpdateExecutor;
    }

    public void dispatch(Update update) {
        telegramUpdateExecutor.execute(() -> {
            try {
                nikiBot.onUpdateReceived(update);
            } catch (Exception e) {
                log.error("Фоновая обработка update {}: {}", update.getUpdateId(), e.getMessage(), e);
            }
        });
    }
}
