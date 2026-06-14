package com.niki.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MentorProfileServiceTest {

    @Test
    void parsesGoalsSectionWithLegacyHeader() throws Exception {
        String raw = """
                ГЛАВНАЯ ЦЕЛЬ:
                Java backend разработчик

                ТЕКУЩИЕ ЦЕЛИ (3):
                сдать экзамен по вождению

                ГЛАВНЫЕ ПРОБЛЕМЫ:
                не могу сдать никак
                """;
        String goals = invokeExtractSection(raw, "ТЕКУЩИЕ ЦЕЛИ");
        assertEquals("сдать экзамен по вождению", goals);
    }

    @Test
    void roundTripDoesNotDuplicateHeaders() {
        MentorProfileService.ProfileData data = MentorProfileService.ProfileData.builder()
                .mainGoal("Java backend")
                .currentGoals("права, работа")
                .problems("волнение")
                .blockers(MentorProfileService.PLACEHOLDER)
                .procrastination(MentorProfileService.PLACEHOLDER)
                .focusRestore("прогулки")
                .remember(MentorProfileService.PLACEHOLDER)
                .build();
        MentorProfileService service = new MentorProfileService(null);
        String formatted = service.formatProfile(data);
        assertEquals(1, countOccurrences(formatted, "ТЕКУЩИЕ ЦЕЛИ"));
        assertFalse(formatted.contains("ТЕКУЩИЕ ЦЕЛИ (3)"));
    }

    private static String invokeExtractSection(String raw, String title) throws Exception {
        Method m = MentorProfileService.class.getDeclaredMethod("extractSection", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw, title);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
