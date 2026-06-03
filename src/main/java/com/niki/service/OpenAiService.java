package com.niki.service;

import com.niki.model.ChatMessage;
import com.niki.model.Goal;
import com.niki.model.User;
import com.niki.repository.ChatMessageRepository;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    private final WebClient openAiWebClient;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MentorProfileService mentorProfileService;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.max-tokens}")
    private int maxTokens;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Transactional
    public String chat(User user, String userText, List<Goal> goals) {
        return chat(user, userText, goals, ChatIntent.DEFAULT);
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals, ChatIntent intent) {
        if (!StringUtils.hasText(apiKey)) {
            return """
                    OpenAI не настроен.
                    Добавь OPENAI_API_KEY в .env (локально) или в Environment на Render.
                    Ключ берётся на platform.openai.com → API keys (это не ChatGPT Plus).""";
        }
        mentorProfileService.ensureDefaultProfile(user);
        String effectiveText = wrapIntent(userText, intent);
        saveMessage(user, "user", effectiveText);
        List<Map<String, String>> messages = buildMessages(user, effectiveText, goals, intent);
        String reply = callOpenAi(messages);
        saveMessage(user, "assistant", reply);
        trimHistory(user.getTelegramId());
        updateMemorySummaryIfNeeded(user);
        return reply;
    }

    private String wrapIntent(String userText, ChatIntent intent) {
        return switch (intent) {
            case CHECK_IN -> "[РЕЖИМ: ЧЕК-ИН] " + userText;
            case NEXT_STEP -> "[РЕЖИМ: СЛЕДУЮЩИЙ ШАГ] " + userText;
            case LEARNING -> "[РЕЖИМ: УЧЁБА] " + userText;
            case MEMORY -> "[РЕЖИМ: ПАМЯТЬ] Покажи что ты помнишь обо мне и что важно обновить.\n" + userText;
            default -> userText;
        };
    }

    public String generateCoverLetter(User user, String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            return "OpenAI API ключ не настроен.";
        }
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "Ты опытный HR-консультант. Пишешь краткие, конкретные " +
                                "сопроводительные письма без шаблонных фраз."),
                Map.of("role", "user", "content", prompt)
        );
        String letter = callOpenAi(messages);
        saveMessage(user, "assistant", "[LETTER]" + letter);
        return letter;
    }

    public String getLastGeneratedLetter(User user) {
        List<ChatMessage> history = chatMessageRepository
                .findByUserTelegramIdOrderByCreatedAtAsc(user.getTelegramId(), PageRequest.of(0, 100));
        return history.stream()
                .filter(m -> m.getRole().equals("assistant") && m.getContent().startsWith("[LETTER]"))
                .reduce((a, b) -> b)
                .map(m -> m.getContent().replace("[LETTER]", ""))
                .orElse("Письмо не найдено. Сначала используй /apply [ссылка]");
    }

    private List<Map<String, String>> buildMessages(User user, String userText, List<Goal> goals, ChatIntent intent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(user, goals, intent)));
        List<ChatMessage> history = chatMessageRepository
                .findByUserTelegramIdOrderByCreatedAtAsc(user.getTelegramId(), PageRequest.of(0, 30));
        for (int i = 0; i < history.size() - 1; i++) {
            ChatMessage msg = history.get(i);
            if (!msg.getContent().startsWith("[LETTER]")) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", userText));
        return messages;
    }

    private String buildSystemPrompt(User user, List<Goal> goals, ChatIntent intent) {
        StringBuilder p = new StringBuilder(mentorProfileService.loadMentorInstructions());
        p.append("\n\nИмя пользователя: ").append(user.getFirstName()).append("\n");

        if (StringUtils.hasText(user.getMentorProfile())) {
            p.append("\n--- ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ---\n")
                    .append(user.getMentorProfile()).append("\n");
        }

        if (user.getMemorySummary() != null && !user.getMemorySummary().isBlank()) {
            p.append("\n--- ДОЛГОСРОЧНАЯ ПАМЯТЬ ---\n")
                    .append(user.getMemorySummary()).append("\n");
        }

        if (!goals.isEmpty()) {
            p.append("\n--- АКТИВНЫЕ ЦЕЛИ (из бота) ---\n");
            for (Goal g : goals) {
                p.append(String.format("- %s (прогресс: %d%%, категория: %s)\n",
                        g.getTitle(), g.getProgress(), g.getCategory()));
            }
        }

        if (intent == ChatIntent.NEXT_STEP) {
            p.append("\nСейчас режим СЛЕДУЮЩИЙ ШАГ: один action на 15–60 мин, связанный с главной целью Java backend.\n");
        } else if (intent == ChatIntent.CHECK_IN) {
            p.append("\nСейчас режим ЧЕК-ИН: сначала спроси энергию 1–10 и что мешает, потом один шаг.\n");
        } else if (intent == ChatIntent.LEARNING) {
            p.append("\nСейчас режим УЧЁБА: фокус на навыках Java backend, без отвлечений.\n");
        }

        return p.toString();
    }

    @SuppressWarnings("unchecked")
    private String callOpenAi(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "max_tokens", maxTokens,
                    "temperature", 0.7
            );
            Map<String, Object> response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                return "Извини, пустой ответ от OpenAI.";
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (WebClientResponseException e) {
            log.error("Ошибка OpenAI {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return mapOpenAiError(e);
        } catch (Exception e) {
            log.error("Ошибка OpenAI: {}", e.getMessage());
            return "Не удалось связаться с OpenAI. Проверь интернет и OPENAI_API_BASE_URL. Попробуй через минуту.";
        }
    }

    private String mapOpenAiError(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401 -> "Неверный OPENAI_API_KEY. Создай новый ключ на platform.openai.com.";
            case 403 -> """
                    OpenAI отклонил запрос (403). Частые причины:
                    • нет баланса на аккаунте OpenAI;
                    • регион заблокирован — сервер на Render обычно работает, локально нужен VPN;
                    • ключ без доступа к модели %s.""".formatted(model).trim();
            case 429 -> "Лимит OpenAI исчерпан. Подожди минуту или пополни баланс.";
            case 404 -> "Модель %s не найдена. Проверь OPENAI_MODEL.".formatted(model);
            default -> "OpenAI вернул ошибку %d. Смотри логи сервера.".formatted(status);
        };
    }

    private void saveMessage(User user, String role, String content) {
        chatMessageRepository.save(ChatMessage.builder()
                .user(user).role(role).content(content).build());
    }

    private void trimHistory(Long telegramId) {
        List<ChatMessage> history = chatMessageRepository
                .findByUserTelegramIdOrderByCreatedAtAsc(telegramId, PageRequest.of(0, 100));
        if (history.size() > 50) {
            Long cutoffId = history.get(history.size() - 50).getId();
            chatMessageRepository.deleteByUserTelegramIdAndIdLessThan(telegramId, cutoffId);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMemorySummaryIfNeeded(User user) {
        long totalMessages = chatMessageRepository.countByUserTelegramId(user.getTelegramId());
        if (totalMessages % 50 != 0 || !StringUtils.hasText(apiKey)) {
            return;
        }
        log.info("Обновляю долгосрочную память для {}", user.getFirstName());
        List<ChatMessage> recent = chatMessageRepository
                .findByUserTelegramIdOrderByCreatedAtAsc(user.getTelegramId(), PageRequest.of(0, 50));
        StringBuilder dialogue = new StringBuilder();
        for (ChatMessage msg : recent) {
            if (!msg.getContent().startsWith("[LETTER]")) {
                String role = msg.getRole().equals("user") ? "Пользователь" : "Ники";
                dialogue.append(role).append(": ").append(msg.getContent()).append("\n");
            }
        }
        List<Map<String, String>> summaryRequest = List.of(
                Map.of("role", "system", "content",
                        "Обнови долгосрочную память о пользователе. Структура (кратко, по пунктам):\n" +
                                "• цели • проекты • навыки • сложности • состояние\n" +
                                "• прокрастинация • что помогает • что мешает • приоритеты\n" +
                                "Только факты из диалога. Без воды. От третьего лица: «Пользователь...»"),
                Map.of("role", "user", "content", "Диалог:\n" + dialogue)
        );
        try {
            Map<String, Object> body = Map.of(
                    "model", model, "messages", summaryRequest,
                    "max_tokens", 300, "temperature", 0.3
            );
            Map<String, Object> response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body).retrieve().bodyToMono(Map.class).block();
            if (response == null) {
                return;
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String summary = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            user.setMemorySummary(summary);
            userRepository.save(user);
            log.info("Память обновлена для {}", user.getFirstName());
        } catch (Exception e) {
            log.error("Ошибка суммаризации: {}", e.getMessage());
        }
    }
}
