package com.niki.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TelegramHtml {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s<>]+");
    private static final String PLACEHOLDER_PREFIX = "\uE000L";

    private TelegramHtml() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String markdownToHtml(String text) {
        if (text == null) {
            return "";
        }
        List<String> protectedLinks = new ArrayList<>();
        String safe = shieldLinks(text, protectedLinks);

        String[] lines = safe.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(formatLine(lines[i]));
        }
        return restoreLinks(sb.toString(), protectedLinks);
    }

    private static String shieldLinks(String text, List<String> vault) {
        String withMarkdownLinks = shieldPattern(text, vault, MARKDOWN_LINK, m ->
                "<a href=\"" + escape(m.group(2)) + "\">" + escape(m.group(1)) + "</a>");
        return shieldPattern(withMarkdownLinks, vault, BARE_URL, m ->
                "<a href=\"" + escape(m.group()) + "\">" + escape(m.group()) + "</a>");
    }

    private static String shieldPattern(String text, List<String> vault, Pattern pattern,
                                        java.util.function.Function<Matcher, String> htmlFactory) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            vault.add(htmlFactory.apply(m));
            m.appendReplacement(sb, Matcher.quoteReplacement(placeholder(vault.size() - 1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String placeholder(int index) {
        return PLACEHOLDER_PREFIX + index + "\uE001";
    }

    private static String restoreLinks(String text, List<String> vault) {
        String restored = text;
        for (int i = 0; i < vault.size(); i++) {
            restored = restored.replace(placeholder(i), vault.get(i));
        }
        return restored;
    }

    private static String formatLine(String line) {
        if (line.contains(PLACEHOLDER_PREFIX)) {
            return line;
        }
        if (line.isBlank()) {
            return "";
        }
        String escaped = escape(line);
        escaped = escaped.replaceAll("\\*([^*\\s][^*\\n]{1,}?)\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("_([^_\\s][^_\\n]{1,}?)_", "<i>$1</i>");
        escaped = escaped.replaceAll("`([^`\\n]+)`", "<code>$1</code>");
        escaped = escaped.replace("*", "");
        return escaped;
    }

    public static String link(String url, String label) {
        return "<a href=\"" + escape(url) + "\">" + escape(label) + "</a>";
    }
}
