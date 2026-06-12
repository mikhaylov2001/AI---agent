package com.niki.service;

import com.niki.model.ChatMessage;
import com.niki.model.Goal;
import com.niki.model.User;
import com.niki.repository.ChatMessageRepository;
import com.niki.repository.UserRepository;
import com.niki.util.MentorResponseFormatter;
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
@Slf4j
public class LlmService {

    private final WebClient llmWebClient;
    private final WebClient anthropicWebClient;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MentorProfileService mentorProfileService;
    private final ConversationFocusService conversationFocusService;

    @Value("${llm.provider:claude}")
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

    @Value("${llm.anthropic.key:}")
    private String anthropicKey;

    @Value("${llm.anthropic.model:claude-sonnet-4-6}")
    private String anthropicModel;

    @Value("${llm.vision.groq-model:llama-3.2-11b-vision-preview}")
    private String groqVisionModel;

    public LlmService(WebClient llmWebClient,
                      WebClient anthropicWebClient,
                      ChatMessageRepository chatMessageRepository,
                      UserRepository userRepository,
                      MentorProfileService mentorProfileService,
                      ConversationFocusService conversationFocusService) {
        this.llmWebClient = llmWebClient;
        this.anthropicWebClient = anthropicWebClient;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.mentorProfileService = mentorProfileService;
        this.conversationFocusService = conversationFocusService;
    }

    private boolean isClaude() {
        String p = provider.toLowerCase(Locale.ROOT);
        return "claude".equals(p) || "anthropic".equals(p);
    }

    private String effectiveApiKey() {
        return isClaude() ? anthropicKey : apiKey;
    }

    private String effectiveModel() {
        return isClaude() ? anthropicModel : model;
    }

    @Transactional
    public String proactiveBrief(User user, List<Goal> goals, String task) {
        String fallback = staticProactiveMessage(task, goals, user);
        if (!StringUtils.hasText(effectiveApiKey())) {
            return fallback;
        }
        mentorProfileService.ensureDefaultProfile(user);
        ConversationFocusService.ResolvedFocus focus =
                conversationFocusService.resolve(user, task, ChatIntent.NEXT_STEP);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", buildSystemPrompt(user, goals, ChatIntent.NEXT_STEP, focus)),
                Map.of("role", "user", "content",
                        "[АВТОПИЛОТ] " + task + "\n\nОтвет СТРОГО в формате:\n▶️ *Сейчас*\nодно конкретное действие")
        );
        String llm = callLlm(messages, proactiveMaxTokens, 0.3);
        if (MentorResponseFormatter.shouldSkipFormatting(llm)) {
            return fallback;
        }
        String formatted = MentorResponseFormatter.format(llm, ChatIntent.NEXT_STEP);
        return formatted.contains("Сейчас") ? formatted : fallback;
    }

    private String staticProactiveMessage(String task, List<Goal> goals, User user) {
        ConversationFocusService.ResolvedFocus focus =
                conversationFocusService.resolve(user, task, ChatIntent.NEXT_STEP);
        String goal = conversationFocusService.primaryGoalTitle(goals, focus);
        String lower = task.toLowerCase();
        if (lower.contains("чек-ин") || lower.contains("чек")) {
            return "▶️ *Сейчас*\nОдин шаг по «" + goal + "» — напиши, что сделал.";
        }
        if (lower.contains("вечер")) {
            return "▶️ *Сейчас*\nИтог дня + один шаг на завтра по «" + goal + "».";
        }
        return "▶️ *Сейчас*\nОдин конкретный шаг по «" + goal + "».";
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals) {
        ConversationFocusService.ResolvedFocus focus =
                conversationFocusService.resolve(user, userText, ChatIntent.DEFAULT);
        return chat(user, userText, goals, focus.intent(), focus);
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals, ChatIntent intent) {
        ConversationFocusService.ResolvedFocus focus =
                conversationFocusService.resolve(user, userText, intent);
        return chat(user, userText, goals, intent, focus);
    }

    @Transactional
    public String chat(User user, String userText, List<Goal> goals, ChatIntent intent,
                       ConversationFocusService.ResolvedFocus focus) {
        if (!StringUtils.hasText(effectiveApiKey())) {
            return isClaude()
                    ? """
                    ИИ не настроен.
                    Добавь CLAUDE_API_KEY в .env или Environment на Render.
                    Ключ: console.anthropic.com → API Keys."""
                    : """
                    ИИ не настроен.
                    Добавь GROQ_API_KEY в .env или Environment на Render.
                    Ключ: console.groq.com → API Keys (бесплатно).""";
        }
        mentorProfileService.ensureDefaultProfile(user);
        String effectiveText = wrapIntent(userText, intent);
        saveMessage(user, "user", effectiveText);
        List<Map<String, String>> messages = buildMessages(user, effectiveText, goals, intent, focus);
        int tokens = tokensForIntent(intent);
        String llm = callLlm(messages, tokens, temperature);
        String reply = MentorResponseFormatter.shouldSkipFormatting(llm)
                ? llm
                : MentorResponseFormatter.format(llm, intent);
        reply = guardReply(focus, intent, userText, reply);
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

    private List<Map<String, String>> buildMessages(User user, String userText, List<Goal> goals,
                                                    ChatIntent intent,
                                                    ConversationFocusService.ResolvedFocus focus) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(user, goals, intent, focus)));
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

    private String buildSystemPrompt(User user, List<Goal> goals, ChatIntent intent,
                                     ConversationFocusService.ResolvedFocus focus) {
        StringBuilder p = new StringBuilder(mentorProfileService.loadMentorInstructions());
        p.append(conversationFocusService.buildPromptSection(focus));
        p.append("\n\nИмя пользователя: ").append(user.getFirstName()).append("\n");
        p.append("Провайдер ИИ: ").append(provider).append(" (").append(effectiveModel()).append(").\n");
        p.append("""
                
                ЖЁСТКИЕ ЛИМИТЫ:
                - ≤ 500 символов, макс. 4 блока
                - Между блоками — пустая строка
                - Заголовок: эмодзи + *Название* + перенос + текст
                - Telegram: без [1], footnotes, таблиц
                """);

        if (StringUtils.hasText(user.getMentorProfile())) {
            p.append("\n--- ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ---\n")
                    .append(user.getMentorProfile()).append("\n");
        }

        if (user.getMemorySummary() != null && !user.getMemorySummary().isBlank()) {
            p.append("\n--- ДОЛГОСРОЧНАЯ ПАМЯТЬ ---\n")
                    .append(user.getMemorySummary()).append("\n");
        }

        if (!goals.isEmpty() && (focus == null || "general".equals(focus.topicId()))) {
            p.append("\n--- АКТИВНЫЕ ЦЕЛИ (справочно, не смешивай с текущей темой) ---\n");
            for (Goal g : goals) {
                p.append(String.format("- %s (прогресс: %d%%, категория: %s)\n",
                        g.getTitle(), g.getProgress(), g.getCategory()));
            }
        }

        p.append("""

                ПОСЛЕДНЕЕ СООБЩЕНИЕ ПОЛЬЗОВАТЕЛЯ — главный сигнал. Не выдумывай, что он писал.
                Mock-собес (HashMap, Spring, «вопрос 1») — только по кнопке «Собес» или явной просьбе.
                """);

        if (intent == ChatIntent.NEXT_STEP) {
            p.append("\nРЕЖИМ: только блок ▶️ *Сейчас* — одно действие, без выдуманного «N мин».\n");
        } else if (intent == ChatIntent.CHECK_IN) {
            p.append("\nРЕЖИМ: 📊 *Чек-ин* (энергия 1–10?) + ▶️ *Сейчас*. Макс. 5 строк.\n");
        } else if (intent == ChatIntent.LEARNING) {
            p.append("\nРЕЖИМ: ▶️ *Сейчас* + ✅ *Проверка* (один вопрос).\n");
        } else if (intent == ChatIntent.INTERVIEW) {
            p.append("\nРЕЖИМ: 3 нумерованных вопроса + ▶️ *Сейчас* (как готовить ответ).\n");
        } else if (intent == ChatIntent.MEMORY) {
            p.append("\nРЕЖИМ: 💡 *Запомнил* — буллеты, макс. 6 пунктов.\n");
        }

        return p.toString();
    }

    private String guardReply(ConversationFocusService.ResolvedFocus focus, ChatIntent intent,
                              String userText, String reply) {
        if (reply == null || reply.isBlank() || focus == null) {
            return reply;
        }
        if (!"jobs".equals(focus.topicId()) && intent != ChatIntent.INTERVIEW) {
            return reply;
        }
        if (intent == ChatIntent.INTERVIEW) {
            return reply;
        }
        String lower = reply.toLowerCase(Locale.ROOT);
        if (looksLikeInterviewDerail(lower)) {
            return """
                    📍 *Контекст*

                    Продолжаем вакансии — не ухожу в собес.

                    ▶️ *Сейчас*

                    Напиши «открой сам» или нажми 💼 *Вакансии* — пришлю подборку с кнопками отклика на HH.""";
        }
        if (looksLikeHallucinatedPercent(userText, lower)) {
            return """
                    📍 *Контекст*

                    Ты прав — я напутал с контекстом. Остаёмся на вакансиях.

                    ▶️ *Сейчас*

                    Напиши «открой сам» — поищу Junior/Middle на HH и дам кнопки отклика.""";
        }
        return reply;
    }

    private static boolean looksLikeInterviewDerail(String lower) {
        return lower.contains("вопрос 1")
                || lower.contains("hashmap")
                || lower.contains("concurrenthashmap")
                || lower.contains("как на реальном собесе")
                || lower.contains("mock-собес")
                || lower.contains("mock собес");
    }

    private static boolean looksLikeHallucinatedPercent(String userText, String replyLower) {
        if (userText == null || !replyLower.contains("30%")) {
            return false;
        }
        return !userText.contains("30");
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
        if (!StringUtils.hasText(effectiveApiKey())) {
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
            String raw = callLlm(messages, 10, 0.1);
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
        if (!StringUtils.hasText(effectiveApiKey())) {
            return isClaude()
                    ? "API ключ Claude не настроен (CLAUDE_API_KEY)."
                    : "API ключ Groq не настроен (GROQ_API_KEY).";
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
        if (!StringUtils.hasText(effectiveApiKey())) {
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
        return callLlmWithRetry(messages, tokens, temp, 1);
    }

    @SuppressWarnings("unchecked")
    private String callLlmWithRetry(List<Map<String, String>> messages, int tokens, double temp, int retriesLeft) {
        try {
            if (isClaude()) {
                return callAnthropic(messages, tokens, temp);
            }
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
            if (e.getStatusCode().value() == 429 && retriesLeft > 0) {
                log.warn("{} 429 — retry через 2 сек", provider);
                sleepQuietly(2000);
                return callLlmWithRetry(messages, tokens, temp, retriesLeft - 1);
            }
            log.error("Ошибка {} {}: {}", provider, e.getStatusCode(), e.getResponseBodyAsString());
            return mapLlmError(e);
        } catch (Exception e) {
            log.error("Ошибка {}: {}", provider, e.getMessage());
            String keyHint = isClaude() ? "CLAUDE_API_KEY" : "GROQ_API_KEY";
            return "Не удалось связаться с " + provider + ". Проверь " + keyHint + " и интернет.";
        }
    }

    @SuppressWarnings("unchecked")
    private String callAnthropic(List<Map<String, String>> messages, int tokens, double temp) {
        String system = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst()
                .orElse("");
        List<Map<String, String>> chatMessages = new ArrayList<>();
        for (Map<String, String> m : messages) {
            if (!"system".equals(m.get("role"))) {
                chatMessages.add(Map.of("role", m.get("role"), "content", m.get("content")));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", anthropicModel);
        body.put("max_tokens", tokens);
        body.put("system", system);
        body.put("messages", chatMessages);
        body.put("temperature", temp);

        Map<String, Object> response = anthropicWebClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null) {
            return "Пустой ответ от Claude.";
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return "Пустой ответ от Claude.";
        }
        return cleanResponse((String) content.get(0).get("text"));
    }

    @Transactional
    public void clearConversationMemory(User user) {
        chatMessageRepository.deleteAllByUserTelegramId(user.getTelegramId());
        user.setMemorySummary(null);
        userRepository.save(user);
        conversationFocusService.clearFocus(user);
        log.info("Память сброшена для {}", user.getTelegramId());
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            case "claude", "anthropic" -> "CLAUDE_API_KEY (console.anthropic.com)";
            case "groq" -> "GROQ_API_KEY (console.groq.com → API Keys)";
            case "perplexity" -> "PERPLEXITY_API_KEY (perplexity.ai → Settings → API)";
            default -> "LLM API key";
        };
        return switch (status) {
            case 401 -> "Неверный ключ. Проверь " + keyHint + ".";
            case 403 -> "Доступ запрещён (403). Проверь лимиты провайдера.";
            case 429 -> "⚠️ Лимит запросов. Подожди 30 сек и нажми снова.";
            case 404 -> "Модель %s не найдена. Проверь LLM_MODEL / CLAUDE_MODEL.".formatted(effectiveModel());
            default -> provider + " вернул ошибку %d. Смотри логи сервера.".formatted(status);
        };
    }

    public String summarizeMaterialForProfile(String rawText, String fileName, String caption) {
        if (!StringUtils.hasText(effectiveApiKey())) {
            return truncate(rawText, 1500);
        }
        String prompt = """
                Сожми материал пользователя для долгосрочной памяти наставника.
                Только факты: навыки, опыт, цели, личные особенности, проценты тестов, слабые места.
                Формат: 4–8 коротких буллетов на русском. Без воды.
                
                Файл: %s
                Подпись: %s
                
                Текст:
                %s
                """.formatted(
                fileName,
                StringUtils.hasText(caption) ? caption : "—",
                truncate(rawText, 5000)
        );
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "Ты архивариус профиля. Только буллеты."),
                Map.of("role", "user", "content", prompt)
        );
        String summary = callLlm(messages, 400, 0.2);
        if (!StringUtils.hasText(summary) || summary.startsWith("⚠️") || summary.contains("не настроен")) {
            return truncate(rawText, 1500);
        }
        return summary;
    }

    @SuppressWarnings("unchecked")
    public String extractTextFromImage(byte[] imageBytes, String mimeType, String caption) {
        if (imageBytes == null || imageBytes.length == 0) {
            return caption != null ? caption : "";
        }
        var normalized = com.niki.util.ImageNormalizer.normalize(imageBytes, mimeType);
        String base64 = Base64.getEncoder().encodeToString(normalized.bytes());
        String mediaType = normalized.mimeType();
        String userText = """
                Это скриншот или фото от пользователя. Извлеки ВСЁ содержимое:
                - все заголовки и подписи
                - все числа и проценты (особенно если это тест личности / СВП)
                - таблицы и списки — построчно
                
                %s
                
                Ответ только фактами, без комментариев.""".formatted(
                StringUtils.hasText(caption) ? "Подпись пользователя: " + caption : "Подписи нет."
        );

        if (StringUtils.hasText(anthropicKey)) {
            String claude = extractWithClaudeVision(base64, mediaType, userText);
            if (StringUtils.hasText(claude)) {
                return claude;
            }
        }
        if (StringUtils.hasText(apiKey)) {
            String groq = extractWithGroqVision(base64, mediaType, userText);
            if (StringUtils.hasText(groq)) {
                return groq;
            }
        }
        return caption != null ? caption : "";
    }

    @SuppressWarnings("unchecked")
    private String extractWithGroqVision(String base64, String mediaType, String userText) {
        try {
            String dataUrl = "data:" + mediaType + ";base64," + base64;
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "text", "text", userText),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", groqVisionModel);
            body.put("messages", List.of(Map.of("role", "user", "content", content)));
            body.put("max_tokens", 800);
            body.put("temperature", 0.1);

            Map<String, Object> response = llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                return "";
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return cleanVisionText((String) message.get("content"));
        } catch (WebClientResponseException e) {
            log.warn("Groq vision failed {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "";
        } catch (Exception e) {
            log.warn("Groq vision failed: {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractWithClaudeVision(String base64, String mediaType, String userText) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", anthropicModel);
            body.put("max_tokens", 800);
            body.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(
                            Map.of("type", "image", "source", Map.of(
                                    "type", "base64",
                                    "media_type", mediaType,
                                    "data", base64
                            )),
                            Map.of("type", "text", "text", userText)
                    )
            )));

            Map<String, Object> response = anthropicWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                return "";
            }
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            return cleanVisionText((String) content.get(0).get("text"));
        } catch (WebClientResponseException e) {
            log.warn("Claude vision failed {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "";
        } catch (Exception e) {
            log.warn("Claude vision failed: {}", e.getMessage());
            return "";
        }
    }

    /** OCR/vision — не обрезаем так агрессивно, как ответы в чат. */
    private static String cleanVisionText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\[\\d+\\]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
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
        if (totalMessages % 50 != 0 || !StringUtils.hasText(effectiveApiKey())) {
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
            String summary = cleanResponse(callLlm(summaryRequest, 300, 0.3));
            if (!StringUtils.hasText(summary) || summary.startsWith("⚠️") || summary.contains("не настроен")) {
                return;
            }
            user.setMemorySummary(summary);
            userRepository.save(user);
            log.info("Память обновлена для {}", user.getFirstName());
        } catch (Exception e) {
            log.error("Ошибка суммаризации: {}", e.getMessage());
        }
    }
}
