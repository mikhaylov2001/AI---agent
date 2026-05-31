package com.niki.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

public record BotResponse(
        String text,
        ReplyKeyboardMarkup replyKeyboard,
        InlineKeyboardMarkup inlineKeyboard,
        boolean disableWebPreview
) {
    public static BotResponse text(String text) {
        return new BotResponse(text, null, null, true);
    }

    public static BotResponse withMainMenu(String text) {
        return new BotResponse(text, TelegramKeyboards.mainMenu(), null, true);
    }

    /** Inline под сообщением (нижнее меню уже закреплено от прошлых ответов). */
    public static BotResponse withInline(String text, InlineKeyboardMarkup inline) {
        return new BotResponse(text, null, inline, true);
    }
}
