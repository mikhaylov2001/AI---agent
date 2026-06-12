package com.niki.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentorResponseFormatterPlainTest {

    @Test
    void sanitizePlainKeepsProfileContent() {
        String profile = """
                🧠 *Твой профиль*

                🎯 *Главная цель*
                Устроиться Java-разработчиком

                _Обновить:_ «📝 Настроить профиль»""";
        String plain = MentorResponseFormatter.sanitizePlain(profile);
        assertFalse(plain.isBlank(), plain);
        assertTrue(plain.contains("профиль"), plain);
    }
}
