package com.niki.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramSendSafetyTest {

    @Test
    void startMessageHtmlWithinTelegramLimit() {
        String welcome = """
                Привет, Дмитрий! 👋 Я *Ники* — карьерный ассистент.

                *Что умею:*
                🎯 цели · 💼 вакансии HH · 📋 отклики · 🧠 профиль
                _Авто-отклики — если подключишь HH и выберешь резюме_

                👇 *Кнопки:*
                🎯 Мои цели · 🧠 Профиль
                💼 Вакансии · 📋 Отклики

                ⚠️ Профиль не заполнен — «📝 Настроить профиль» (4 шага, ~2 мин).""";
        String html = TelegramHtml.markdownToHtml(welcome);
        assertFalse(html.isBlank());
        assertTrue(html.length() <= 4096, "len=" + html.length());
        assertFalse(html.contains("\uE000"), "unrestored link placeholder in: " + html);
        assertValidTelegramHtml(html);
    }

    @Test
    void profileDisplayHtmlWithinTelegramLimit() {
        String profile = """
                🧠 *Твой профиль*

                🎯 *Главная цель*
                Устроиться Java-разработчиком

                📌 *Цели*
                1. Устроиться Java-разработчиком (собесы, резюме, отклики на HH)

                💡 *Важно помнить*
                Не любит воду и пустую мотивацию

                _Обновить:_ «📝 Настроить профиль»""";
        String html = TelegramHtml.markdownToHtml(profile);
        assertFalse(html.isBlank());
        assertTrue(html.length() <= 4096);
        assertValidTelegramHtml(html);
    }

    private static void assertValidTelegramHtml(String html) {
        int opens = count(html, "<b>");
        int closes = count(html, "</b>");
        assertEquals(opens, closes, "unbalanced <b> in: " + html);
        opens = count(html, "<i>");
        closes = count(html, "</i>");
        assertEquals(opens, closes, "unbalanced <i> in: " + html);
        assertFalse(html.contains("<<"), html);
    }

    private static int count(String s, String sub) {
        int c = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }
}
