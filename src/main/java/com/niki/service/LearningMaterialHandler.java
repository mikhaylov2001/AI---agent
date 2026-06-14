package com.niki.service;

import com.niki.bot.BotResponse;
import com.niki.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Сохраняет списки изученных тем — не путать с поиском вакансий по словам Java/Spring.
 */
@Service
@RequiredArgsConstructor
public class LearningMaterialHandler {

    private static final Pattern NUMBERED_TECH_LINE = Pattern.compile(
            "(?im)^\\s*\\d+[.)]\\s*.*(java|spring|kafka|docker|k8s|kubernetes|leetcode|hibernate|redis|postgres|git)\\b");

    private final MentorProfileService mentorProfileService;
    private final ConversationFocusService conversationFocusService;

    @Transactional
    public Optional<BotResponse> tryHandle(User user, String text) {
        if (!JobTextPatterns.isLearningMaterial(text)) {
            return Optional.empty();
        }
        conversationFocusService.persistTopic(user, "java_career");
        String stored = trimForProfile(text);
        mentorProfileService.mergeIntoProfile(user, "learning", "Прогресс по темам", stored);

        int topicCount = estimateTopicCount(text);
        String summary = topicCount > 0
                ? "Записал *" + topicCount + "* тем/пунктов в профиль."
                : "Записал список в профиль.";

        return Optional.of(BotResponse.withMainMenu("""
                ✅ *Прогресс по учёбе сохранён*
                
                %s
                Раздел: *Чему учусь сейчас* — смотри 🧠 *Профиль*.
                
                _Это учёба, не вакансии._ Для HH жми 💼 *Вакансии* отдельно.""".formatted(summary)));
    }

    private static String trimForProfile(String text) {
        if (text.length() <= 2500) {
            return text.trim();
        }
        return text.substring(0, 2497).trim() + "\n…";
    }

    private static int estimateTopicCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int checks = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '✅' || c == '✔') {
                checks++;
            }
        }
        if (checks >= 2) {
            return checks;
        }
        int lines = 0;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.length() >= 4 && !t.startsWith("---")) {
                lines++;
            }
        }
        return Math.max(lines, 1);
    }
}
