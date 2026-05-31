package com.niki.service;

public interface NikiMessageSender {

    void sendMessage(Long chatId, String text);
}
