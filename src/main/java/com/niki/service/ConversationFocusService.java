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
 * Держит одну тему диалога, чтобы ответы не прыгали между правами, Java, HH и т.д.
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
        String lastAssistant = lastContent(recent, "assistant");

        if (requestedIntent != ChatIntent.DEFAULT) {
            String topic = topicFromIntent(requestedIntent);
            persistTopic(user, topic);
            return new ResolvedFocus(topic, labelFor(topic), requestedIntent);
        }

        String stored = loadStoredFocus(user);

        if (stored != null && isContinuationMessage(lower)) {
            return new ResolvedFocus(stored, labelFor(stored), ChatIntent.DEFAULT);
        }

        String fromText = detectTopicFromText(lower);
        if (fromText != null) {
            persistTopic(user, fromText);
            return new ResolvedFocus(fromText, labelFor(fromText), ChatIntent.DEFAULT);
        }

        if (text.length() <= 48 && lastAssistant != null) {
            String fromReply = detectFromReply(lower, lastAssistant);
            if (fromReply != null) {
                persistTopic(user, fromReply);
                ChatIntent intent = intentForShortReply(fromReply, lower);
                return new ResolvedFocus(fromReply, labelFor(fromReply), intent);
            }
        }

        if (stored != null) {
            return new ResolvedFocus(stored, labelFor(stored), ChatIntent.DEFAULT);
        }

        String fromUserDialogue = inferFromUserMessages(recent);
        if (fromUserDialogue != null) {
            persistTopic(user, fromUserDialogue);
            return new ResolvedFocus(fromUserDialogue, labelFor(fromUserDialogue), ChatIntent.DEFAULT);
        }

        return new ResolvedFocus("general", "Общий диалог", ChatIntent.DEFAULT);
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
                    Следуй последнему сообщению пользователя. Не смешивай разные цели в одном ответе.
                    Блок «Контекст» — одна строка только про то, о чём сейчас говорите.
                    """;
        }
        return """

                --- ТЕКУЩАЯ ТЕМА ДИАЛОГА ---
                %s

                Правила (строго):
                - Ответ только по этой теме. Не переключайся на другие цели, пока пользователь сам не попросил.
                - Блок «Контекст» — одна строка только про эту тему (без списка всех целей и без чужих дедлайнов).
                - Не смешивай в одном сообщении права, Java, машину, HH, предпринимательство и «энергию».
                - Mock-собес и вопросы (HashMap, Spring…) — ЗАПРЕЩЕНЫ, если тема не «Собес».
                - Не приписывай пользователю слова и числа (типа «30%%»), которых нет в его последнем сообщении.
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
        String needle = switch (focus != null ? focus.topicId() : "general") {
            case "driving" -> "прав";
            case "jobs" -> "работ";
            case "java_career" -> "java";
            default -> null;
        };
        if (needle != null) {
            for (com.niki.model.Goal g : goals) {
                if (g.getTitle().toLowerCase(Locale.ROOT).contains(needle)) {
                    return g.getTitle();
                }
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

    private static String lastContent(List<ChatMessage> messages, String role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (role.equals(m.getRole()) && StringUtils.hasText(m.getContent())) {
                return m.getContent();
            }
        }
        return null;
    }

    String detectTopicFromText(String lower) {
        if (JobTextPatterns.isLearningMaterial(lower)) {
            return "java_career";
        }
        if (JobTextPatterns.isJobRelated(lower)) {
            return "jobs";
        }
        if (matchesAny(lower, "java", "дjava", "джава", "spring", "backend", "kotlin", "leetcode", "leet code",
                "core java", "hibernate", "maven", "gradle")) {
            return "java_career";
        }
        if (matchesAny(lower, "собес", "собесед", "mock", "интервью")) {
            return "java_career";
        }
        if (matchesAny(lower, "права", "пдд", "вожд", "автошкол", "экзамен на права", "гибдд")) {
            return "driving";
        }
        if (matchesAny(lower, "общени", "soft skill", "разговор с незнаком")) {
            return "communication";
        }
        if (matchesAny(lower, "бизнес", "предприним", "стартап", "монетиз")) {
            return "entrepreneur";
        }
        return null;
    }

    static boolean isContinuationMessage(String lower) {
        if (JobTextPatterns.wantsBotToSearch(lower)) {
            return true;
        }
        return matchesAny(lower,
                "тупой", "облажался", "накосячил", "что за", "почему", "контекст", "меняется",
                "не понял", "не поняла", "бред", "зачем", "серьёзно", "серьезно", "ну ", "ну,")
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

    String detectFromReply(String lower, String lastAssistant) {
        String assistant = lastAssistant.toLowerCase(Locale.ROOT);
        if (asksChoice(assistant, "права", "java") || asksChoice(assistant, "права", "java-собес")
                || asksChoice(assistant, "права", "собес")) {
            if (matchesAny(lower, "java", "дjava", "джава", "j", "2", "собес", "backend", "spring")) {
                return "java_career";
            }
            if (matchesAny(lower, "права", "пdd", "пдд", "1", "вожд", "гибдд")) {
                return "driving";
            }
        }
        if (assistant.contains("что учим") || assistant.contains("что учить")) {
            if (matchesAny(lower, "java", "дjava", "джава", "собес", "spring")) {
                return "java_career";
            }
            if (matchesAny(lower, "права", "пdd", "пдд", "вожд")) {
                return "driving";
            }
        }
        return null;
    }

    private static boolean asksChoice(String assistant, String a, String b) {
        return assistant.contains(a) && assistant.contains(b)
                && (assistant.contains("или") || assistant.contains("?"));
    }

    private static ChatIntent intentForShortReply(String topicId, String lower) {
        if ("java_career".equals(topicId)) {
            return lower.contains("собес") ? ChatIntent.INTERVIEW : ChatIntent.LEARNING;
        }
        return ChatIntent.DEFAULT;
    }

    private static String topicFromIntent(ChatIntent intent) {
        return switch (intent) {
            case LEARNING, INTERVIEW, NEXT_STEP -> "java_career";
            case CHECK_IN -> "general";
            default -> "general";
        };
    }

    private static String labelFor(String topicId) {
        return switch (topicId) {
            case "java_career" -> "Java / карьера / собесы / учёба backend";
            case "driving" -> "Права / вождение / ПДД";
            case "jobs" -> "Вакансии / HH / отклики";
            case "communication" -> "Навык общения";
            case "entrepreneur" -> "Предпринимательство";
            default -> "Общий диалог";
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
