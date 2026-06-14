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

    /** Запрос списка резюме — не путать с поиском вакансий. */
    static boolean isResumeListRequest(String lower) {
        if (lower.equals("резюме") || lower.equals("📄 резюме")) {
            return true;
        }
        return containsAny(lower, "мои резюме", "список резюме", "hh_resumes", "hh resumes");
    }

    /** Список изученных тем / roadmap — не HH-поиск. */
    static boolean isLearningMaterial(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = normalize(text);
        if (containsAny(lower,
                "изучен", "theory", "теория", "прогресс по", "roadmap", "чеклист", "чек-лист",
                "пройден", "освоил", "учебный план", "план обучения", "скидываю тем",
                "изученные тем", "что прошёл", "что прошел", "мой прогресс", "leetcode - топ",
                "leetcode топ", "неделя 1", "неделя 2", "неделя 3", "неделя 4",
                "неделя 5", "неделя 6", "неделя 7", "неделя 8")) {
            return true;
        }
        long checks = text.chars().filter(c -> c == '✅' || c == '✔' || c == '☑').count();
        if (checks >= 2) {
            return true;
        }
        String[] lines = text.split("\n");
        if (lines.length >= 3 && countTechStudyLines(lines) >= 3) {
            return true;
        }
        if (lines.length >= 2 && NUMBERED_TECH_LINE.matcher(text).find() && countTechStudyLines(lines) >= 2) {
            return true;
        }
        return false;
    }

    private static final Pattern NUMBERED_TECH_LINE = Pattern.compile(
            "(?i)(java|spring|kafka|docker|kubernetes|k8s|leetcode|hibernate|redis|postgres|cap-теор|system design)");

    private static int countTechStudyLines(String[] lines) {
        int n = 0;
        for (String line : lines) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.isBlank() || l.startsWith("---")) {
                continue;
            }
            if (NUMBERED_TECH_LINE.matcher(l).find()
                    || containsAny(l, "core java", "spring boot", "микросервис", "outbox", "saga",
                    "circuit breaker", "rate limiter", "mock-собес", "ci/cd")) {
                n++;
            }
        }
        return n;
    }

    static boolean isJobRelated(String lower) {
        if (isResumeListRequest(lower)) {
            return false;
        }
        if (isLearningMaterial(lower)) {
            return false;
        }
        if (containsAny(lower,
                "ваканс", "hh", "headhunter", "отклик", "head hunter",
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
        if (lower.contains("java") && containsAny(lower, "dev", "backend", "работ", "ваканс", "hh")
                && !containsAny(lower, "core java", "java core", "теория", "theory", "изучен", "пройден")) {
            return true;
        }
        if (lower.contains("java") && lower.contains("spring")
                && containsAny(lower, "developer", "backend", "ваканс", "hh", "работ", "ищу")) {
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

    /** Короткое продолжение диалога про вакансии (только если уже в теме jobs). */
    static boolean isJobFollowUp(String lower) {
        return containsAny(lower,
                "ещё", "еще", "дальше", "следующ", "другие ваканс", "найди", "ищи", "подбери",
                "отклик", "hh", "headhunter", "ваканс", "удалён", "удален", "remote",
                "java backend", "spring boot developer", "middle", "junior", "отправь ваканс",
                "пришли ваканс", "дай ваканс", "покажи ваканс", "открой сам", "сам найди");
    }

    static String extractSearchQuery(String text, String savedQuery) {
        if (isLearningMaterial(text)) {
            return defaultQuery(savedQuery);
        }
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
        if (lower.contains("spring") && containsAny(lower, "developer", "backend", "ваканс", "работ", "ищу")) {
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
