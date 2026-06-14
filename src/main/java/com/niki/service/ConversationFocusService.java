package com.niki.service;

import com.niki.model.ChatMessage;
import com.niki.model.User;
import com.niki.repository.ChatMessageRepository;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Держит тему диалога — в основном вакансии и карьера.
 */
@Service
@RequiredArgsConstructor
public class ConversationFocusService {

    private static final String PAYLOAD_PREFIX = "focus:";

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final UserSessionService sessionService;

    public record ResolvedFocus(String topicId, String topicLabel, ChatIntent intent) {
    }

    @Transactional
    public ResolvedFocus resolve(User user, String userText, ChatIntent requestedIntent) {
        String text = userText == null ? "" : userText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        List<ChatMessage> recent = loadRecent(user.getTelegramId(), 10);

        String stored = loadStoredFocus(user);

        if (stored != null && isContinuationMessage(lower)) {
            return new ResolvedFocus(stored, labelFor(stored), ChatIntent.DEFAULT);
        }

        String fromText = detectTopicFromText(lower);
        if (fromText != null) {
            persistTopic(user, fromText);
            return new ResolvedFocus(fromText, labelFor(fromText), ChatIntent.DEFAULT);
        }

        if (stored != null) {
            return new ResolvedFocus(stored, labelFor(stored), ChatIntent.DEFAULT);
        }

        String fromUserDialogue = inferFromUserMessages(recent);
        if (fromUserDialogue != null) {
            persistTopic(user, fromUserDialogue);
            return new ResolvedFocus(fromUserDialogue, labelFor(fromUserDialogue), ChatIntent.DEFAULT);
        }

        return new ResolvedFocus("general", "Карьера / вакансии", ChatIntent.DEFAULT);
    }

    @Transactional
    public void persistTopic(User user, String topicId) {
        persistFocus(user, topicId);
    }

    public String loadTopicId(User user) {
        return loadStoredFocus(user);
    }

    public String buildPromptSection(ResolvedFocus focus) {
        if (focus == null || "general".equals(focus.topicId())) {
            return """

                    --- ТЕМА ДИАЛОГА ---
                    Помогай с целями, вакансиями HH, откликами и собесами. Не уходи в учёбу и личные темы без запроса.
                    """;
        }
        return """

                --- ТЕКУЩАЯ ТЕМА ---
                %s

                Ответ только по этой теме. Не смешивай цели, HH и посторонние задачи в одном сообщении.
                """.formatted(focus.topicLabel());
    }

    @Transactional
    public void clearFocus(User user) {
        userRepository.findByTelegramId(user.getTelegramId()).ifPresent(u -> {
            if (isFocusPayload(u.getSessionPayload())) {
                u.setSessionPayload(null);
                userRepository.save(u);
            }
        });
    }

    public String primaryGoalTitle(List<com.niki.model.Goal> goals, ResolvedFocus focus) {
        if (goals == null || goals.isEmpty()) {
            return "Java backend";
        }
        for (com.niki.model.Goal g : goals) {
            if (g.getTitle().toLowerCase(Locale.ROOT).contains("работ")
                    || g.getTitle().toLowerCase(Locale.ROOT).contains("java")) {
                return g.getTitle();
            }
        }
        return goals.get(0).getTitle();
    }

    private List<ChatMessage> loadRecent(Long telegramId, int limit) {
        List<ChatMessage> all = chatMessageRepository
                .findByUserTelegramIdOrderByCreatedAtAsc(telegramId, PageRequest.of(0, limit + 5));
        int from = Math.max(0, all.size() - limit);
        return all.subList(from, all.size());
    }

    String detectTopicFromText(String lower) {
        if (JobTextPatterns.isJobRelated(lower)) {
            return "jobs";
        }
        if (matchesAny(lower, "цел", "прогресс", "goal")) {
            return "goals";
        }
        if (matchesAny(lower, "собес", "собесед", "интервью", "interview")) {
            return "interview";
        }
        if (matchesAny(lower, "java", "spring", "backend", "резюме", "hh", "ваканс", "отклик")) {
            return "jobs";
        }
        return null;
    }

    static boolean isContinuationMessage(String lower) {
        if (JobTextPatterns.wantsBotToSearch(lower)) {
            return true;
        }
        return matchesAny(lower,
                "тупой", "облажался", "почему", "не понял", "зачем", "ну ", "ну,")
                || lower.length() <= 24;
    }

    private String inferFromUserMessages(List<ChatMessage> recent) {
        StringBuilder userOnly = new StringBuilder();
        for (ChatMessage m : recent) {
            if ("user".equals(m.getRole()) && StringUtils.hasText(m.getContent())) {
                userOnly.append(' ').append(m.getContent());
            }
        }
        return detectTopicFromText(userOnly.toString().toLowerCase(Locale.ROOT));
    }

    private static String labelFor(String topicId) {
        return switch (topicId) {
            case "jobs" -> "Вакансии / HH / отклики";
            case "goals" -> "Цели и прогресс";
            case "interview" -> "Собеседования";
            default -> "Карьера / вакансии";
        };
    }

    private static boolean matchesAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private void persistFocus(User user, String topicId) {
        if (sessionService.getState(user.getTelegramId()) != UserSessionService.State.NONE) {
            return;
        }
        userRepository.findByTelegramId(user.getTelegramId()).ifPresent(u -> {
            u.setSessionPayload(PAYLOAD_PREFIX + topicId);
            userRepository.save(u);
        });
    }

    private String loadStoredFocus(User user) {
        return userRepository.findByTelegramId(user.getTelegramId())
                .map(User::getSessionPayload)
                .filter(ConversationFocusService::isFocusPayload)
                .map(p -> p.substring(PAYLOAD_PREFIX.length()))
                .orElse(null);
    }

    private static boolean isFocusPayload(String payload) {
        return payload != null && payload.startsWith(PAYLOAD_PREFIX);
    }
}
