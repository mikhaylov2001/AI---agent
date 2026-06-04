package com.niki.util;

public final class TelegramHtml {

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
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(formatLine(lines[i]));
        }
        return sb.toString();
    }

    private static String formatLine(String line) {
        String escaped = escape(line);
        // Только пары *слово* длиной ≥2 — иначе «*К*онтекст» ломает Telegram
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
