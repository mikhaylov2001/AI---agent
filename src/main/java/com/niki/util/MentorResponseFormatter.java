package com.niki.util;

import com.niki.service.ChatIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentorResponseFormatter {

    private static final Pattern FLUFF_START = Pattern.compile(
            "(?is)^\\s*(конечно|отличный вопрос|хороший вопрос|давай разбер[её]м|рад помочь)[,!.:—\\-]*\\s*",
            Pattern.UNICODE_CASE);

    private static final Pattern HEADER_LINE = Pattern.compile(
            "^(?:([📍⚠️▶️💡📊✅])\\s*)?\\*?([^*\\n:]+?)\\*?\\s*:?\\s*(.*)$");

    private static final Set<String> HEADER_NAMES_ONLY = Set.of(
            "контекст", "вижу", "зона роста", "проблема", "сейчас", "шаг",
            "запомнил", "память", "чек-ин", "проверка"
    );

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("вижу", "Контекст"),
            Map.entry("контекст", "Контекст"),
            Map.entry("проблема", "Зона роста"),
            Map.entry("зона роста", "Зона роста"),
            Map.entry("где проблема", "Зона роста"),
            Map.entry("шаг", "Сейчас"),
            Map.entry("сейчас", "Сейчас"),
            Map.entry("следующий шаг", "Сейчас"),
            Map.entry("память", "Запомнил"),
            Map.entry("запомнил", "Запомнил"),
            Map.entry("что запомнить", "Запомнил"),
            Map.entry("чек-ин", "Чек-ин"),
            Map.entry("проверка", "Проверка")
    );

    private static final Map<String, String> HEADER_EMOJI = Map.of(
            "Контекст", "📍",
            "Зона роста", "⚠️",
            "Сейчас", "▶️",
            "Запомнил", "💡",
            "Чек-ин", "📊",
            "Проверка", "✅"
    );

    private MentorResponseFormatter() {
    }

    /** Не трогать ошибки и системные сообщения — иначе ломается текст. */
    public static boolean shouldSkipFormatting(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String lower = raw.toLowerCase();
        return lower.contains("лимит запросов")
                || lower.contains("groq_api")
                || lower.contains("не настроен")
                || lower.contains("не удалось")
                || lower.contains("пустой ответ")
                || lower.contains("неверный ключ")
                || lower.contains("ошибк")
                || lower.contains("console.groq.com")
                || lower.startsWith("⚠️");
    }

    public static String format(String raw, ChatIntent intent) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (shouldSkipFormatting(raw)) {
            return sanitizePlain(raw);
        }

        String text = stripFluff(normalizeWhitespace(raw.trim()));

        // Обычный диалог — не перестраиваем, иначе «Дима» → ▶️ *Д* + «има…»
        if (intent == ChatIntent.DEFAULT) {
            return sanitizePlain(text);
        }

        if (intent == ChatIntent.NEXT_STEP) {
            return formatMinimal(text);
        }

        List<Block> blocks = parseBlocks(text);
        if (blocks.isEmpty() || !hasValidContent(blocks)) {
            return plainStructuredFallback(text, intent);
        }
        return renderBlocks(blocks, intent);
    }

    private static String stripFluff(String text) {
        String prev;
        do {
            prev = text;
            text = FLUFF_START.matcher(text).replaceFirst("");
            text = text.replaceFirst("(?i)^\\s*вот что я думаю[:\\.]?\\s*", "");
        } while (!text.equals(prev));
        return text.trim();
    }

    private static String normalizeWhitespace(String text) {
        return text.replaceAll("\\r\\n", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static List<Block> parseBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        for (String line : text.split("\n")) {
            Block block = parseLineBlock(line.trim());
            if (block != null) {
                blocks.add(block);
            }
        }
        if (blocks.isEmpty()) {
            for (String paragraph : text.split("\\n\\n+")) {
                Block block = parseParagraph(paragraph.trim());
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        return mergeDuplicateTitles(blocks);
    }

    private static boolean hasValidContent(List<Block> blocks) {
        return blocks.stream().anyMatch(b -> b.body().length() >= 8
                && !HEADER_NAMES_ONLY.contains(b.body().toLowerCase().trim()));
    }

    private static Block parseParagraph(String paragraph) {
        if (paragraph.isBlank()) {
            return null;
        }
        String[] lines = paragraph.split("\n", 2);
        String firstLine = lines[0].trim();
        if (!looksLikeHeaderLine(firstLine)) {
            return null;
        }
        Matcher m = HEADER_LINE.matcher(firstLine);
        if (!m.matches()) {
            return null;
        }
        String title = normalizeTitle(m.group(2));
        if (!isKnownCanonicalTitle(title)) {
            return null;
        }
        String inlineBody = m.group(3) != null ? m.group(3).trim() : "";
        String body = !inlineBody.isBlank() ? inlineBody
                : (lines.length > 1 ? lines[1].trim() : "");
        if (!isValidBody(body, title)) {
            return null;
        }
        return new Block(title, body.replaceAll("\\s+", " ").trim());
    }

    private static Block parseLineBlock(String line) {
        if (line.isBlank()) {
            return null;
        }
        String rest = line.trim();
        for (String icon : HEADER_EMOJI.values()) {
            if (rest.startsWith(icon)) {
                rest = rest.substring(icon.length()).trim();
                break;
            }
        }
        Matcher m = Pattern.compile("^\\*([^*\\n]+)\\*(?:\\s*:)?\\s*(.+)$").matcher(rest);
        if (!m.matches()) {
            m = Pattern.compile("^([^:\\n]+?)\\s*:\\s*(.+)$").matcher(rest);
            if (!m.matches()) {
                return null;
            }
        }
        String title = normalizeTitle(m.group(1));
        if (!isKnownCanonicalTitle(title)) {
            return null;
        }
        String body = m.group(2).trim();
        if (!isValidBody(body, title)) {
            return null;
        }
        return new Block(title, body);
    }

    private static boolean looksLikeHeaderLine(String line) {
        if (line.isBlank()) {
            return false;
        }
        for (String icon : HEADER_EMOJI.values()) {
            if (line.startsWith(icon)) {
                return true;
            }
        }
        if (line.contains("*")) {
            Matcher star = Pattern.compile("\\*([^*\\n]{2,}?)\\*").matcher(line);
            if (star.find()) {
                String key = star.group(1).trim().toLowerCase()
                        .replaceAll("[:\\.]+$", "");
                return HEADER_ALIASES.containsKey(key) || HEADER_EMOJI.containsKey(normalizeTitle(star.group(1)));
            }
        }
        int colon = line.indexOf(':');
        if (colon > 0) {
            String beforeColon = line.substring(0, colon).trim().toLowerCase()
                    .replaceAll("\\*+", "")
                    .replaceAll("[:\\.]+$", "");
            return HEADER_ALIASES.containsKey(beforeColon);
        }
        return false;
    }

    private static boolean isKnownCanonicalTitle(String title) {
        return title != null && HEADER_EMOJI.containsKey(title);
    }

    private static boolean isValidBody(String body, String title) {
        if (body.isBlank() || body.length() < 8) {
            return false;
        }
        String b = body.toLowerCase().trim();
        return !HEADER_NAMES_ONLY.contains(b) && !b.equals(title.toLowerCase());
    }

    private static List<Block> mergeDuplicateTitles(List<Block> blocks) {
        Map<String, StringBuilder> merged = new LinkedHashMap<>();
        for (Block b : blocks) {
            merged.computeIfAbsent(b.title(), k -> new StringBuilder())
                    .append(merged.get(b.title()).length() > 0 ? " " : "")
                    .append(b.body());
        }
        List<Block> result = new ArrayList<>();
        merged.forEach((title, body) -> result.add(new Block(title, body.toString().trim())));
        return result;
    }

    private static String normalizeTitle(String raw) {
        if (raw == null) {
            return "Контекст";
        }
        String key = raw.trim().toLowerCase()
                .replaceAll("\\*+", "")
                .replaceAll("[:\\.]+$", "")
                .trim();
        return HEADER_ALIASES.getOrDefault(key, capitalize(key));
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String renderBlocks(List<Block> blocks, ChatIntent intent) {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (!isKnownCanonicalTitle(block.title())
                    || shouldSkip(block, intent)
                    || !isValidBody(block.body(), block.title())) {
                continue;
            }
            String emoji = HEADER_EMOJI.getOrDefault(block.title(), "▶️");
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(emoji).append(" *").append(block.title()).append("*\n");
            sb.append(block.body());
        }
        String result = sb.toString().trim();
        if (result.length() > 900) {
            result = result.substring(0, 897) + "…";
        }
        return result.isEmpty() ? plainStructuredFallback("", intent) : result;
    }

    private static boolean shouldSkip(Block block, ChatIntent intent) {
        return intent == ChatIntent.CHECK_IN && "Контекст".equals(block.title());
    }

    private static String formatMinimal(String text) {
        List<Block> blocks = parseBlocks(text);
        String body = blocks.stream()
                .filter(b -> "Сейчас".equals(b.title()))
                .map(Block::body)
                .findFirst()
                .orElseGet(() -> blocks.stream()
                        .filter(b -> isValidBody(b.body(), b.title()))
                        .map(Block::body)
                        .findFirst()
                        .orElse(text.replaceAll("(?i)^▶️\\s*", "").trim()));

        if (body.isBlank() || body.length() < 8) {
            body = "25 мин · один шаг по Java backend — напиши что сделал.";
        }
        return "▶️ *Сейчас*\n" + body.trim();
    }

    private static String plainStructuredFallback(String text, ChatIntent intent) {
        if (text.isBlank()) {
            return "▶️ *Сейчас*\nНапиши, над чем работаем — дам один шаг.";
        }
        String clean = sanitizePlain(text);
        return switch (intent) {
            case CHECK_IN -> "📊 *Чек-ин*\nЭнергия 1–10? Что мешает?\n\n▶️ *Сейчас*\n" + firstLine(clean);
            case NEXT_STEP -> "▶️ *Сейчас*\n" + firstLine(clean);
            case MEMORY -> "💡 *Запомнил*\n" + clean;
            default -> clean.length() > 200
                    ? "▶️ *Сейчас*\n" + firstLine(clean)
                    : clean;
        };
    }

    /** Убирает сломанные asterisk — причину «К онтекст» в Telegram. */
    public static String sanitizePlain(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\*{2,}", "")
                .replaceAll("(?<!\\*)\\*(?!\\*)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstLine(String text) {
        String line = text.lines().findFirst().orElse(text);
        return line.length() > 120 ? line.substring(0, 117) + "…" : line;
    }

    private record Block(String title, String body) {
    }
}
