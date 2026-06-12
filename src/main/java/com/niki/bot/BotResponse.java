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

    public static BotResponse withCareerMenu(String text) {
        return new BotResponse(text, TelegramKeyboards.careerMenu(), null, true);
    }

    public static BotResponse withCareerMenuAndInline(String text, InlineKeyboardMarkup inline) {
        return new BotResponse(text, TelegramKeyboards.careerMenu(), inline, true);
    }

    /** Inline + нижнее меню остаётся от предыдущих ответов. */
    public static BotResponse withInlineAndMenu(String text, InlineKeyboardMarkup inline) {
        return new BotResponse(text, TelegramKeyboards.mainMenu(), inline, true);
    }
}
