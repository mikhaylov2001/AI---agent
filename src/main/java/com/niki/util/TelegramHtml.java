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

    /** Конвертирует *bold* и _italic_ в HTML для Telegram. */
    public static String markdownToHtml(String text) {
        if (text == null) {
            return "";
        }
        String escaped = escape(text);
        escaped = escaped.replaceAll("\\*([^*\\n]+)\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("_([^_\\n]+)_", "<i>$1</i>");
        escaped = escaped.replaceAll("`([^`\\n]+)`", "<code>$1</code>");
        return escaped;
    }

    public static String link(String url, String label) {
        return "<a href=\"" + escape(url) + "\">" + escape(label) + "</a>";
    }
}
