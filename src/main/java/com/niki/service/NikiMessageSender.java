package com.niki.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface NikiMessageSender {

    void sendMessage(Long chatId, String text);

    void sendMessageWithInline(Long chatId, String text, InlineKeyboardMarkup inline);
}
