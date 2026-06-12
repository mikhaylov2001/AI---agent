package com.niki.bot;

import com.niki.util.MentorResponseFormatter;
import com.niki.util.TelegramHtml;
import com.niki.util.TelegramLimits;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
final class TelegramSendHelper {

    private TelegramSendHelper() {
    }

    static boolean send(AbsSender bot, Long chatId, BotResponse response) {
        String source = safeText(response.text());

        if (trySend(bot, chatId, source, response.inlineKeyboard(), response.replyKeyboard(),
                response.disableWebPreview(), true)) {
            return true;
        }
        if (trySend(bot, chatId, source, response.inlineKeyboard(), response.replyKeyboard(),
                response.disableWebPreview(), false)) {
            return true;
        }
        if (response.inlineKeyboard() != null
                && trySend(bot, chatId, source, null, response.replyKeyboard(), response.disableWebPreview(), false)) {
            return true;
        }
        if (trySend(bot, chatId, source, null, null, response.disableWebPreview(), false)) {
            return true;
        }
        return false;
    }

    private static boolean trySend(AbsSender bot, Long chatId, String source,
                                   InlineKeyboardMarkup inline,
                                   ReplyKeyboardMarkup reply,
                                   boolean disablePreview,
                                   boolean html) {
        String text = html
                ? TelegramLimits.truncate(TelegramHtml.markdownToHtml(source), TelegramLimits.MAX_MESSAGE_LENGTH)
                : TelegramLimits.truncate(MentorResponseFormatter.sanitizePlain(source), TelegramLimits.MAX_MESSAGE_LENGTH);
        if (text.isBlank()) {
            text = "…";
        }

        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text);
        if (html) {
            builder.parseMode("HTML");
        }
        if (disablePreview) {
            builder.disableWebPagePreview(true);
        }
        if (inline != null) {
            builder.replyMarkup(inline);
        } else if (reply != null) {
            builder.replyMarkup(reply);
        }

        try {
            bot.execute(builder.build());
            return true;
        } catch (TelegramApiException e) {
            log.warn("Telegram send (html={}, inline={}, reply={}): {}",
                    html, inline != null, reply != null, e.getMessage());
            return false;
        }
    }

    private static String safeText(String text) {
        return text == null || text.isBlank() ? "…" : text;
    }
}
