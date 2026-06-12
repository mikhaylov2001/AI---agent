package com.niki.service;

import com.niki.model.User;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JobTextPatterns {

    private static final Pattern JAVA_DEV_TYPO = Pattern.compile("java\\s+devel\\w*", Pattern.CASE_INSENSITIVE);

    private JobTextPatterns() {
    }

    static boolean isJobRelated(String lower) {
        if (containsAny(lower,
                "ваканс", "hh", "headhunter", "отклик", "резюме", "head hunter",
                "джуниор", "junior", "мидл", "middle", "мидла", "hh.ru",
                "python backend", "backend developer", "java developer", "java backend",
                "java разработ", "разработчик java", "работа java", "ищу работу")) {
            return true;
        }
        if (JAVA_DEV_TYPO.matcher(lower).find()) {
            return true;
        }
        if (lower.contains("developer") || lower.contains("develover") || lower.contains("develo")) {
            return true;
        }
        if (lower.contains("java") && containsAny(lower, "dev", "backend", "spring", "работ", "ваканс", "hh")) {
            return true;
        }
        return wantsBotToSearch(lower)
                || requestsJuniorMiddle(lower)
                || isJobSearchRequest(lower)
                || wantsVacancySelection(lower);
    }

    static boolean wantsBotToSearch(String lower) {
        return containsAny(lower,
                "открой сам", "сам открой", "сделай сам", "найди сам", "сам найди",
                "открой сама", "сам сделай", "сам ищи", "ищи сам", "выбери сам",
                "найди ", "найди", "ищи ", "подбери", "пришли", "присылай", "отправь",
                "покажи ваканс", "дай ваканс", "скинь", "варианты", "подборк");
    }

    static boolean wantsVacancySelection(String lower) {
        return containsAny(lower, "лучш", "топ ", "топ-", "выбери", "отбери")
                && containsAny(lower, "ваканс", "hh", "java", "dev", "работ", "отклик", "вариант");
    }

    static boolean requestsJuniorMiddle(String lower) {
        return (lower.contains("джун") || lower.contains("junior"))
                && (lower.contains("мид") || lower.contains("middle") || lower.contains("мидл"));
    }

    static boolean isJobSearchRequest(String lower) {
        return lower.contains("ваканс")
                && containsAny(lower, "джун", "junior", "мид", "middle", "мидл", "только", "java", "developer");
    }

    /** Продолжение диалога про вакансии — не уходить в LLM. */
    static boolean isJobThreadContinuation(String lower) {
        return wantsBotToSearch(lower)
                || wantsVacancySelection(lower)
                || containsAny(lower,
                "должен уметь", "ты должен", "отправил", "отправлял", "требован",
                "не видишь", "не видишь", "сообщениями", "ссылк", "работает",
                "почему не", "не можешь", "нет интернета", "не открываешь");
    }

    static String extractSearchQuery(String text, String savedQuery) {
        if (!StringUtils.hasText(text)) {
            return defaultQuery(savedQuery);
        }
        String trimmed = text.trim();
        Matcher javaDev = JAVA_DEV_TYPO.matcher(trimmed);
        if (javaDev.find()) {
            return "Java developer";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("python") && lower.contains("backend")) {
            return "Python backend developer";
        }
        if (lower.contains("spring")) {
            return "Java Spring Boot developer";
        }
        if (lower.contains("backend") || lower.contains("java") || lower.contains("developer")
                || lower.contains("devel") || lower.contains("разработ")) {
            return "Java backend developer";
        }
        if (lower.contains("ваканс") || lower.contains("работ")) {
            return defaultQuery(savedQuery);
        }
        return defaultQuery(savedQuery);
    }

    private static String defaultQuery(String savedQuery) {
        return StringUtils.hasText(savedQuery) ? savedQuery : "Java backend developer";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
