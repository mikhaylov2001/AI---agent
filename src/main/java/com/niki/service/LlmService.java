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
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final WebClient llmWebClient;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MentorProfileService mentorProfileService;

    @Value("${llm.provider:groq}")
    private String provider;

    @Value("${llm.api.model}")
    private String model;

    @Value("${llm.api.max-tokens}")
    private int maxTokens;

    @Value("${llm.api.temperature:0.35}")
    private double temperature;

    @Value("${llm.api.proactive-max-tokens:220}")
    private int proactiveMaxTokens;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Transactional
    public String proactiveBrief(User user, List<Goal> goals, String task) {
        if (!StringUtils.hasText(apiKey)) {
            return "Настрой GROQ_API_KEY — тогда смогу писать сам по расписанию.";
        }
        mentorProfileService.ensureDefaultProfile(user);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", buildSystemPrompt(user, goals, ChatIntent.NEXT_STEP)),
                Map.of("role", "user", "content", "[АВТОПИЛОТ] " + task + "\n\nОтвет: макс. 5 строк, сразу ▶️ Шаг, без воды.")
        );
        return callLlm(messages, proactiveMaxTokens, 0.3);
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals) {
        return chat(user, userText, goals, ChatIntent.DEFAULT);
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals, ChatIntent intent) {
        if (!StringUtils.hasText(apiKey)) {
            return """
                    ИИ не настроен.
                    Добавь GROQ_API_KEY в .env или Environment на Render.
                    Ключ: console.groq.com → API Keys (бесплатно).""";
        }
        mentorProfileService.ensureDefaultProfile(user);
        String effectiveText = wrapIntent(userText, intent);
        saveMessage(user, "user", effectiveText);
        List<Map<String, String>> messages = buildMessages(user, effectiveText, goals, intent);
        int tokens = tokensForIntent(intent);
        String reply = callLlm(messages, tokens, temperature);
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
            case INTERVIEW -> "[РЕЖИМ: СОБЕС] Подготовь к собеседованию: mock-вопросы по Java/Spring, разбор вакансии.\n" + userText;
            default -> userText;
        };
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
                .findByUserTelegramIdOrderByCreatedAtAsc(user.getTelegramId(), PageRequest.of(0, 15));
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
        p.append("Провайдер ИИ: ").append(provider).append(" (").append(model).append(").\n");
        p.append("""
                
                ЖЁСТКИЕ ЛИМИТЫ (нарушать нельзя):
                - Обычный ответ: ≤ 600 символов, 4 блока максимум
                - Каждый блок — 1–2 короткие строки
                - Нет блока = не пиши заголовок
                - Telegram: без ссылок [1], без footnotes, без markdown-таблиц
                """);

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
            p.append("\nРЕЖИМ СЛЕДУЮЩИЙ ШАГ: только ▶️ Шаг, 1–2 строки. Без 📍 и ⚠️.\n");
        } else if (intent == ChatIntent.CHECK_IN) {
            p.append("\nРЕЖИМ ЧЕК-ИН: «Энергия 1–10? Что мешает?» + ▶️ один шаг. Макс. 4 строки.\n");
        } else if (intent == ChatIntent.LEARNING) {
            p.append("\nРЕЖИМ УЧЁБА: ▶️ шаг + 1 пример. Без теории на полстраницы.\n");
        } else if (intent == ChatIntent.INTERVIEW) {
            p.append("\nРЕЖИМ СОБЕС: ровно 3 вопроса (нумерованный список) + ▶️ как готовить ответ. Макс. 8 строк.\n");
        } else if (intent == ChatIntent.MEMORY) {
            p.append("\nРЕЖИМ ПАМЯТЬ: список фактов буллетами, макс. 6 пунктов, без советов.\n");
        }

        return p.toString();
    }

    private int tokensForIntent(ChatIntent intent) {
        return switch (intent) {
            case NEXT_STEP, CHECK_IN -> 180;
            case LEARNING, INTERVIEW -> 350;
            case MEMORY -> 300;
            default -> maxTokens;
        };
    }

    public int scoreVacancyMatch(User user, String vacancyTitle, String description) {
        if (!StringUtils.hasText(apiKey)) {
            return 50;
        }
        mentorProfileService.ensureDefaultProfile(user);
        String prompt = """
                Оцени соответствие кандидата вакансии от 0 до 100.
                Ответь ТОЛЬКО числом (например: 72).
                
                Профиль кандидата:
                %s
                
                Вакансия: %s
                Описание: %s
                """.formatted(
                user.getMentorProfile() != null ? user.getMentorProfile() : "",
                vacancyTitle,
                description.length() > 600 ? description.substring(0, 600) : description
        );
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "Ты HR-аналитик. Отвечай только числом 0-100."),
                Map.of("role", "user", "content", prompt)
        );
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", 10);
            body.put("temperature", 0.1);
            Map<String, Object> response = llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                return 50;
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String raw = cleanResponse((String) ((Map<String, Object>) choices.get(0).get("message")).get("content"));
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return 50;
            }
            return Math.min(100, Math.max(0, Integer.parseInt(digits.substring(0, Math.min(3, digits.length())))));
        } catch (Exception e) {
            log.warn("scoreVacancyMatch: {}", e.getMessage());
            return 50;
        }
    }

    public String generateCoverLetter(User user, String vacancyName, String description, String resumeSummary, int matchScore) {
        if (!StringUtils.hasText(apiKey)) {
            return "API ключ Groq не настроен (GROQ_API_KEY).";
        }
        mentorProfileService.ensureDefaultProfile(user);
        String prompt = String.format("""
                Напиши сопроводительное письмо (3-4 предложения, русский, без шаблонов).
                Match score: %d%%
                
                Профиль:
                %s
                
                Резюме HH:
                %s
                
                Вакансия: %s
                Описание: %s
                """,
                matchScore,
                user.getMentorProfile(),
                StringUtils.hasText(resumeSummary) ? resumeSummary : "(резюме не выбрано)",
                vacancyName,
                description
        );
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "Ты HR-консультант. Краткие конкретные письма без клише и ссылок."),
                Map.of("role", "user", "content", prompt)
        );
        String letter = callLlm(messages);
        saveMessage(user, "assistant", "[LETTER]" + letter);
        return letter;
    }

    public String rewriteCoverLetter(User user, String letter, String instruction) {
        if (!StringUtils.hasText(apiKey)) {
            return letter;
        }
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "Перепиши сопроводительное письмо по инструкции. Только текст письма."),
                Map.of("role", "user", "content", "Письмо:\n" + letter + "\n\nИнструкция: " + instruction)
        );
        String updated = callLlm(messages);
        saveMessage(user, "assistant", "[LETTER]" + updated);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private String callLlm(List<Map<String, String>> messages) {
        return callLlm(messages, maxTokens, temperature);
    }

    @SuppressWarnings("unchecked")
    private String callLlm(List<Map<String, String>> messages, int tokens, double temp) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", tokens);
            body.put("temperature", temp);

            Map<String, Object> response = llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                return "Пустой ответ от " + provider + ".";
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return cleanResponse((String) message.get("content"));
        } catch (WebClientResponseException e) {
            log.error("Ошибка {} {}: {}", provider, e.getStatusCode(), e.getResponseBodyAsString());
            return mapLlmError(e);
        } catch (Exception e) {
            log.error("Ошибка {}: {}", provider, e.getMessage());
            return "Не удалось связаться с " + provider + ". Проверь GROQ_API_KEY и интернет.";
        }
    }

    /** Убираем citation-маркеры и лишние переносы для Telegram. */
    private static String cleanResponse(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\[\\d+\\]", "")
                .replaceAll("(?m)^\\s*(Конечно|Отличный вопрос|Хороший вопрос)[,!]?.{0,20}\\n", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (cleaned.length() > 1200) {
            cleaned = cleaned.substring(0, 1197) + "…";
        }
        return cleaned;
    }

    private String mapLlmError(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        String keyHint = switch (provider.toLowerCase(Locale.ROOT)) {
            case "groq" -> "GROQ_API_KEY (console.groq.com → API Keys)";
            case "perplexity" -> "PERPLEXITY_API_KEY (perplexity.ai → Settings → API)";
            default -> "LLM API key";
        };
        return switch (status) {
            case 401 -> "Неверный ключ. Проверь " + keyHint + ".";
            case 403 -> "Доступ запрещён (403). Проверь лимиты провайдера.";
            case 429 -> "Лимит запросов исчерпан. Подожди минуту (Groq free tier).";
            case 404 -> "Модель %s не найдена. Проверь LLM_MODEL.".formatted(model);
            default -> provider + " вернул ошибку %d. Смотри логи сервера.".formatted(status);
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
            Map<String, Object> response = llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body).retrieve().bodyToMono(Map.class).block();
            if (response == null) {
                return;
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String summary = cleanResponse(
                    (String) ((Map<String, Object>) choices.get(0).get("message")).get("content"));
            user.setMemorySummary(summary);
            userRepository.save(user);
            log.info("Память обновлена для {}", user.getFirstName());
        } catch (Exception e) {
            log.error("Ошибка суммаризации: {}", e.getMessage());
        }
    }
}
