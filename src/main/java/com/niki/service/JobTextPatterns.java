package com.niki.service;

import java.util.Locale;

final class JobTextPatterns {

    private JobTextPatterns() {
    }

    static boolean isJobRelated(String lower) {
        return containsAny(lower,
                "ваканс", "hh", "headhunter", "отклик", "резюме", "джуниор", "junior",
                "мидл", "middle", "мидла", "hh.ru", "python backend", "backend developer")
                || wantsBotToSearch(lower)
                || requestsJuniorMiddle(lower);
    }

    static boolean wantsBotToSearch(String lower) {
        return containsAny(lower,
                "открой сам", "сам открой", "сделай сам", "найди сам", "сам найди",
                "открой сама", "сам сделай", "сам ищи", "ищи сам");
    }

    static boolean requestsJuniorMiddle(String lower) {
        return (lower.contains("джун") || lower.contains("junior"))
                && (lower.contains("мид") || lower.contains("middle") || lower.contains("мидл"));
    }

    static boolean isJobSearchRequest(String lower) {
        return lower.contains("ваканс") && containsAny(lower, "джун", "junior", "мид", "middle", "мидл", "только");
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
