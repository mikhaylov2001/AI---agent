package com.niki.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public final class TelegramKeyboards {

    public static final String BTN_GOALS = "🎯 Мои цели";
    public static final String BTN_ADD_GOAL = "➕ Добавить цель";
    public static final String BTN_JOBS = "💼 Найти вакансии";
    public static final String BTN_CONNECT_HH = "🔗 Подключить HH";
    public static final String BTN_RESUMES = "📄 Мои резюме";
    public static final String BTN_HELP = "❓ Помощь";

    private TelegramKeyboards() {
    }

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);
        keyboard.setSelective(false);
        keyboard.setKeyboard(List.of(
                row(BTN_GOALS, BTN_ADD_GOAL),
                row(BTN_JOBS, BTN_CONNECT_HH),
                row(BTN_RESUMES, BTN_HELP)
        ));
        return keyboard;
    }

    public static InlineKeyboardMarkup startInlineMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                inlineButton(BTN_GOALS, "goals"),
                inlineButton(BTN_JOBS, "jobs")
        ));
        rows.add(List.of(
                inlineButton(BTN_CONNECT_HH, "connect_hh"),
                inlineButton(BTN_HELP, "help")
        ));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup jobSearchSuggestions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton("Java developer", "jobs:Java developer"),
                        inlineButton("Python", "jobs:Python")
                ),
                List.of(
                        inlineButton("Йошкар-Ола", "jobs:Йошкар-Ола"),
                        inlineButton("Удалёнка", "jobs:удаленная работа")
                )
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
