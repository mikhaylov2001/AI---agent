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
        String out = MentorResponseFormatter.format(raw, ChatIntent.LEARNING);
        assertFalse(out.toLowerCase().contains("отличный"), "out was: " + out);
        assertTrue(out.contains("📍 *Контекст*"), "out was: " + out);
        assertTrue(out.contains("▶️ *Сейчас*"), "out was: " + out);
    }

    @Test
    void nextStepOnlyShowsAction() {
        String raw = "📍 Контекст: blah\n▶️ Сейчас: 15 мин · LeetCode easy";
        String out = MentorResponseFormatter.format(raw, ChatIntent.NEXT_STEP);
        assertTrue(out.startsWith("▶️ *Сейчас*"));
        assertFalse(out.contains("Контекст"));
    }

    @Test
    void defaultChatDoesNotSplitWords() {
        String raw = "Дима, что хочешь изучить следующим? 🎯\nВот что логично после пройденного:\nЧто выбираешь?";
        String out = MentorResponseFormatter.format(raw, ChatIntent.DEFAULT);
        assertFalse(out.contains("*Д*"), "out was: " + out);
        assertFalse(out.contains("*В*"), "out was: " + out);
        assertFalse(out.contains("*Ч*"), "out was: " + out);
        assertTrue(out.contains("Дима"), "out was: " + out);
    }

    @Test
    void doesNotTreatPlainSentencesAsHeaders() {
        String raw = "Контекст\nонтекст тела";
        String out = MentorResponseFormatter.format(raw, ChatIntent.CHECK_IN);
        assertFalse(out.contains("▶️ *К*"), "out was: " + out);
    }
}
