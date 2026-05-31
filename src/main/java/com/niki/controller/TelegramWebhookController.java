package com.niki.controller;

import com.niki.bot.NikiBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final NikiBot nikiBot;

    @PostMapping("/telegram/webhook")
    public void onWebhookUpdate(@RequestBody Update update) {
        log.debug("Webhook update id={}", update.getUpdateId());
        nikiBot.onUpdateReceived(update);
    }
}
