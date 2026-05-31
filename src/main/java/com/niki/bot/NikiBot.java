package com.niki.bot;

import com.niki.handler.CommandHandler;
import com.niki.service.NikiMessageSender;
import com.niki.service.ReminderService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@Slf4j
public class NikiBot extends TelegramLongPollingBot implements NikiMessageSender {

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final CommandHandler commandHandler;
    private final ReminderService reminderService;

    public NikiBot(CommandHandler commandHandler,
                   ReminderService reminderService,
                   @Value("${telegram.bot.token}") String token) {
        super(token);
        this.commandHandler = commandHandler;
        this.reminderService = reminderService;
    }

    @PostConstruct
    public void init() {
        reminderService.setMessageSender(this);
        log.info("Ники запущен! @{}", botUsername);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        var message = update.getMessage();
        try {
            sendMessage(message.getChatId(), commandHandler.handle(message));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            sendMessage(message.getChatId(), "Что-то пошло не так 😅 Попробуй ещё раз.");
        }
    }

    @Override
    public void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .disableWebPagePreview(true)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки в {}: {}", chatId, e.getMessage());
            try {
                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text)
                        .disableWebPagePreview(true)
                        .build());
            } catch (TelegramApiException ex) {
                log.error("Повторная ошибка отправки: {}", ex.getMessage());
            }
        }
    }
}
