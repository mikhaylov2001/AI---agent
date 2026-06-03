package com.niki.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public final class TelegramKeyboards {

    // Нижнее меню (Reply)
    public static final String BTN_NEXT_STEP = "📋 След. шаг";
    public static final String BTN_CHECKIN = "📊 Чек-ин";
    public static final String BTN_GOALS = "🎯 Мои цели";
    public static final String BTN_PROFILE = "🧠 Профиль";
    public static final String BTN_JOBS = "💼 Вакансии";
    public static final String BTN_LEARNING = "📚 Учёба";
    public static final String BTN_SETUP_PROFILE = "📝 Настроить профиль";
    public static final String BTN_CONNECT_HH = "🔗 HH";
    public static final String BTN_RESUMES = "📄 Резюме";
    public static final String BTN_HELP = "❓ Помощь";

    private TelegramKeyboards() {
    }

    /** Основная навигация — 4 ряда, самое частое сверху. */
    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);
        keyboard.setSelective(false);
        keyboard.setKeyboard(List.of(
                row(BTN_NEXT_STEP, BTN_CHECKIN),
                row(BTN_GOALS, BTN_PROFILE),
                row(BTN_JOBS, BTN_LEARNING),
                row(BTN_CONNECT_HH, BTN_HELP)
        ));
        return keyboard;
    }

    /** Меню карьеры / HH. */
    public static ReplyKeyboardMarkup careerMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setKeyboard(List.of(
                row(BTN_JOBS, BTN_RESUMES),
                row(BTN_CONNECT_HH, BTN_NEXT_STEP),
                row("◀️ Главное меню")
        ));
        return keyboard;
    }

    public static InlineKeyboardMarkup startInlineMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton(BTN_NEXT_STEP, "next_step"),
                        inlineButton(BTN_CHECKIN, "checkin")
                ),
                List.of(
                        inlineButton(BTN_GOALS, "goals"),
                        inlineButton(BTN_PROFILE, "profile")
                ),
                List.of(
                        inlineButton(BTN_SETUP_PROFILE, "setup_profile"),
                        inlineButton(BTN_JOBS, "jobs")
                )
        ));
        return markup;
    }

    public static InlineKeyboardMarkup profileActions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(inlineButton(BTN_SETUP_PROFILE, "setup_profile")),
                List.of(
                        inlineButton(BTN_GOALS, "goals"),
                        inlineButton(BTN_CHECKIN, "checkin")
                )
        ));
        return markup;
    }

    public static InlineKeyboardMarkup jobSearchSuggestions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton("Java backend", "jobs:Java backend"),
                        inlineButton("Spring Boot", "jobs:Spring Boot developer")
                ),
                List.of(
                        inlineButton("Junior Java", "jobs:Junior Java"),
                        inlineButton("Удалёнка", "jobs:Java удаленная работа")
                ),
                List.of(inlineButton("◀️ Главное меню", "main_menu"))
        ));
        return markup;
    }

    private static KeyboardRow row(String... labels) {
        KeyboardRow row = new KeyboardRow();
        for (String label : labels) {
            row.add(KeyboardButton.builder().text(label).build());
        }
        return row;
    }

    private static InlineKeyboardButton inlineButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
