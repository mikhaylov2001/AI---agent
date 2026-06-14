package com.niki.util;

import com.niki.service.ChatIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentorResponseFormatterTest {

    @Test
    void stripsFluffAndFormatsBlocks() {
        String raw = """
                Отличный вопрос! Вот что я думаю:
                
                📍 *Вижу:* хочешь Spring
                ▶️ *Шаг:* 20 мин почитать docs
                """;
        String out = MentorResponseFormatter.format(raw);
        assertFalse(out.toLowerCase().contains("отличный"), "out was: " + out);
        assertTrue(out.contains("📍 *Контекст*"), "out was: " + out);
        assertTrue(out.contains("▶️ *Сейчас*"), "out was: " + out);
    }

    @Test
    void defaultChatWithBlocksKeepsStructure() {
        String raw = """
                📍 *Контекст*
                Ищем Java backend на HH
                
                ▶️ *Сейчас*
                Напиши «Java developer» — пришлю вакансии
                """;
        String out = MentorResponseFormatter.format(raw);
        assertTrue(out.contains("📍 *Контекст*"), "out was: " + out);
        assertTrue(out.contains("▶️ *Сейчас*"), "out was: " + out);
    }

    @Test
    void defaultChatDoesNotSplitWords() {
        String raw = "Дима, что хочешь изучить следующим? 🎯\nВот что логично после пройденного:\nЧто выбираешь?";
        String out = MentorResponseFormatter.format(raw);
        assertFalse(out.contains("*Д*"), "out was: " + out);
        assertTrue(out.contains("Дима"), "out was: " + out);
    }

    @Test
    void stripsTimersFromWholeMessage() {
        String raw = "▶️ *Сейчас*\n15 мин · открой hh.ru";
        String out = MentorResponseFormatter.format(raw);
        assertFalse(out.contains("15 мин"), "out was: " + out);
    }
}
