package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationFocusServiceTest {

    private final ConversationFocusService service = new ConversationFocusService(null, null, null);

    @Test
    void jobMessageSetsJobsTopic() {
        assertEquals("jobs", service.detectTopicFromText("найди вакансии java backend"));
    }

    @Test
    void goalMessageSetsGoalsTopic() {
        assertEquals("goals", service.detectTopicFromText("обнови прогресс цели"));
    }

    @Test
    void continuationKeepsTopicOnComplaint() {
        assertTrue(ConversationFocusService.isContinuationMessage("ты тупой почему контекст меняется"));
    }

    @Test
    void promptSectionForJobs() {
        ConversationFocusService.ResolvedFocus focus =
                new ConversationFocusService.ResolvedFocus("jobs", "Вакансии / HH", ChatIntent.DEFAULT);
        String section = service.buildPromptSection(focus);
        assertTrue(section.contains("ТЕКУЩАЯ ТЕМА"));
        assertTrue(section.contains("Вакансии"));
    }
}
