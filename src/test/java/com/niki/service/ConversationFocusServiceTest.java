package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationFocusServiceTest {

    private final ConversationFocusService service = new ConversationFocusService(null, null, null);

    @Test
    void shortReplyJavaAfterChoiceStaysOnJava() {
        String assistant = "📍 Контекст\nЭнергия 7. Что учим: права или Java-собес?";
        assertEquals("java_career", service.detectFromReply("джава", assistant));
        assertEquals("java_career", service.detectFromReply("java", assistant));
    }

    @Test
    void shortReplyRightsAfterChoice() {
        String assistant = "Что учим: права или Java-собес?";
        assertEquals("driving", service.detectFromReply("права", assistant));
    }

    @Test
    void explicitJavaMessageSetsCareerTopic() {
        assertEquals("java_career", service.detectTopicFromText("хочу разобрать core java"));
    }

    @Test
    void continuationKeepsTopicOnComplaint() {
        assertTrue(ConversationFocusService.isContinuationMessage("ты тупой почему контекст меняется"));
    }

    @Test
    void promptSectionForbidsTopicMixing() {
        ConversationFocusService.ResolvedFocus focus =
                new ConversationFocusService.ResolvedFocus("java_career", "Java / карьера", ChatIntent.LEARNING);
        String section = service.buildPromptSection(focus);
        assertTrue(section.contains("ТЕКУЩАЯ ТЕМА"));
        assertTrue(section.contains("Не переключайся"));
    }
}
