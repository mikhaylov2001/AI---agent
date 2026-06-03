package com.niki.util;

import com.niki.service.ChatIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Приводит ответ LLM к единому чистому формату для Telegram.
 */
public final class MentorResponseFormatter {

    private static final Pattern FLUFF_START = Pattern.compile(
            "(?is)^\\s*(конечно|отличный вопрос|хороший вопрос|давай разбер[её]м|рад помочь)[,!.:—\\-]*\\s*",
            Pattern.UNICODE_CASE);

    private static final Pattern HEADER_LINE = Pattern.compile(
            "^(?:([📍⚠️▶️💡📊✅])\\s*)?\\*?([^*\\n:]+?)\\*?\\s*:?\\s*(.*)$");

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

    public static String format(String raw, ChatIntent intent) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String text = stripFluff(normalizeWhitespace(raw.trim()));

        if (intent == ChatIntent.NEXT_STEP) {
            return formatMinimal(text);
        }

        List<Block> blocks = parseBlocks(text);
        if (blocks.isEmpty()) {
            return wrapFallback(text, intent);
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

    private static Block parseParagraph(String paragraph) {
        if (paragraph.isBlank()) {
            return null;
        }
        String[] lines = paragraph.split("\n", 2);
        Matcher m = HEADER_LINE.matcher(lines[0].trim());
        if (!m.matches()) {
            return null;
        }
        String title = normalizeTitle(m.group(2));
        String inlineBody = m.group(3) != null ? m.group(3).trim() : "";
        String body = !inlineBody.isBlank() ? inlineBody
                : (lines.length > 1 ? lines[1].trim() : "");
        if (body.isBlank()) {
            return null;
        }
        return new Block(title, body.replaceAll("\\s+", " ").trim());
    }

    private static Block parseLineBlock(String line) {
        if (line.isBlank()) {
            return null;
        }
        String emoji = null;
        String rest = line.trim();
        for (Map.Entry<String, String> e : HEADER_EMOJI.entrySet()) {
            String icon = e.getValue();
            if (rest.startsWith(icon)) {
                emoji = icon;
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
        String body = m.group(2).trim();
        if (body.isBlank()) {
            return null;
        }
        if (emoji != null && !title.equals(normalizeTitle(stripEmojiLabel(rest)))) {
            // title from *bold* takes precedence
        }
        return new Block(title, body);
    }

    private static String stripEmojiLabel(String s) {
        return s.replaceAll("^\\*([^*]+)\\*.*", "$1");
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
            if (shouldSkip(block, intent)) {
                continue;
            }
            String emoji = HEADER_EMOJI.getOrDefault(block.title(), "•");
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
        return result.isEmpty() ? wrapFallback("", intent) : result;
    }

    private static boolean shouldSkip(Block block, ChatIntent intent) {
        if (intent == ChatIntent.CHECK_IN && "Контекст".equals(block.title())) {
            return true;
        }
        return false;
    }

    private static String formatMinimal(String text) {
        List<Block> blocks = parseBlocks(text);
        String body = blocks.stream()
                .filter(b -> "Сейчас".equals(b.title()))
                .map(Block::body)
                .findFirst()
                .orElseGet(() -> {
                    if (!blocks.isEmpty()) {
                        return blocks.get(blocks.size() - 1).body();
                    }
                    return text.replaceAll("(?i)^▶️\\s*", "").trim();
                });
        if (body.isBlank()) {
            body = "Напиши, над чем работаем — дам один шаг.";
        }
        return "▶️ *Сейчас*\n" + body.trim();
    }

    private static String wrapFallback(String text, ChatIntent intent) {
        if (text.isBlank()) {
            return "▶️ *Сейчас*\nНапиши, над чем работаем — дам один конкретный шаг.";
        }
        return switch (intent) {
            case CHECK_IN -> "📊 *Чек-ин*\nЭнергия 1–10? Что мешает?\n\n▶️ *Сейчас*\n" + firstLine(text);
            case MEMORY -> "💡 *Запомнил*\n" + text;
            default -> "📍 *Контекст*\n" + firstLine(text) + "\n\n▶️ *Сейчас*\nУточни задачу — дам один шаг.";
        };
    }

    private static String firstLine(String text) {
        String line = text.lines().findFirst().orElse(text);
        return line.length() > 120 ? line.substring(0, 117) + "…" : line;
    }

    private record Block(String title, String body) {
    }
}
