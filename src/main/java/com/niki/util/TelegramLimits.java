package com.niki.util;

/**
 * Ограничения Telegram Bot API для исходящих сообщений.
 */
public final class TelegramLimits {

    public static final int MAX_MESSAGE_LENGTH = 4096;

    private TelegramLimits() {
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 1) {
            return "…";
        }
        return text.substring(0, max - 1) + "…";
    }
}
