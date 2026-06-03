package com.niki.bot;

import com.niki.handler.CommandHandler;
import com.niki.service.NikiMessageSender;
import com.niki.service.ProactiveAgentService;
import com.niki.util.TelegramHtml;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@Slf4j
public class NikiBot extends TelegramLongPollingBot implements NikiMessageSender {

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final CommandHandler commandHandler;
    private final ProactiveAgentService proactiveAgentService;

    public NikiBot(CommandHandler commandHandler,
                   ProactiveAgentService proactiveAgentService,
                   @Value("${telegram.bot.token}") String token) {
        super(token);
        this.commandHandler = commandHandler;
        this.proactiveAgentService = proactiveAgentService;
    }

    @PostConstruct
    public void init() {
        proactiveAgentService.setMessageSender(this);
        registerBotCommands();
        log.info("Ники запущен! @{}", botUsername);
    }

    private void registerBotCommands() {
        try {
            execute(new SetMyCommands(List.of(
                    new BotCommand("start", "Начать / меню"),
                    new BotCommand("next_step", "Следующий шаг"),
                    new BotCommand("checkin", "Чек-ин состояния"),
                    new BotCommand("goals", "Мои цели"),
                    new BotCommand("progress", "Прогресс цели"),
                    new BotCommand("profile", "Мой профиль"),
                    new BotCommand("memory", "Что помню"),
                    new BotCommand("setup_profile", "Настроить профиль"),
                    new BotCommand("jobs", "Java вакансии"),
                    new BotCommand("applications", "Мои отклики"),
                    new BotCommand("interview", "Подготовка к собесу"),
                    new BotCommand("learning", "Помощь с учёбой"),
                    new BotCommand("connect_hh", "Подключить HH.ru"),
                    new BotCommand("autopilot", "Автопилот on/off"),
                    new BotCommand("help", "Навигация")
            ), new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.warn("Не удалось зарегистрировать команды меню: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
                return;
            }
            if (!update.hasMessage()) {
                return;
            }
            var message = update.getMessage();
            if (!message.hasText()) {
                sendPlain(message.getChatId(), "Пока понимаю только текст. Напиши /start или используй кнопки.");
                return;
            }
            log.info("Сообщение от {}: {}", message.getFrom().getId(), message.getText());
            sendResponse(message.getChatId(), commandHandler.handle(message));
        } catch (Exception e) {
            log.error("Ошибка обработки update: {}", e.getMessage(), e);
            Long chatId = resolveChatId(update);
            if (chatId != null) {
                sendPlain(chatId, "Что-то пошло не так. Нажми /start");
            }
        }
    }

    private Long resolveChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    private void sendPlain(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(TelegramKeyboards.mainMenu())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение: {}", e.getMessage());
        }
    }

    private void handleCallback(Update update) throws TelegramApiException {
        var callback = update.getCallbackQuery();
        if (callback.getMessage() == null) {
            return;
        }
        Long chatId = callback.getMessage().getChatId();
        execute(AnswerCallbackQuery.builder()
                .callbackQueryId(callback.getId())
                .build());
        BotResponse response = commandHandler.handleCallback(callback.getFrom().getId(), callback.getData());
        sendResponse(chatId, response);
    }

    private void sendResponse(Long chatId, BotResponse response) {
        try {
            execute(buildMessage(chatId, response));
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки в {}: {}", chatId, e.getMessage());
        }
    }

    private SendMessage buildMessage(Long chatId, BotResponse response) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(TelegramHtml.markdownToHtml(response.text()))
                .parseMode("HTML");
        if (response.disableWebPreview()) {
            builder.disableWebPagePreview(true);
        }
        if (response.inlineKeyboard() != null) {
            builder.replyMarkup(response.inlineKeyboard());
        } else if (response.replyKeyboard() != null) {
            builder.replyMarkup(response.replyKeyboard());
        }
        return builder.build();
    }

    @Override
    public void sendMessage(Long chatId, String text) {
        sendResponse(chatId, BotResponse.withMainMenu(text));
    }

    @Override
    public void sendMessageWithInline(Long chatId, String text, InlineKeyboardMarkup inline) {
        sendResponse(chatId, BotResponse.withInlineAndMenu(text, inline));
    }
}
