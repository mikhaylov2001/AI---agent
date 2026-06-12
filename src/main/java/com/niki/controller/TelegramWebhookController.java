package com.niki.controller;

import com.niki.service.TelegramUpdateDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "telegram.delivery-mode", havingValue = "webhook", matchIfMissing = true)
public class TelegramWebhookController {

    private final TelegramUpdateDispatcher updateDispatcher;

    @Value("${telegram.webhook.secret-token:}")
    private String secretToken;

    @PostMapping("/telegram/webhook")
    public void onUpdate(@RequestBody Update update,
                         @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String headerSecret) {
        if (StringUtils.hasText(secretToken) && !secretToken.equals(headerSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid webhook secret");
        }
        log.info("Webhook update id={}", update.getUpdateId());
        updateDispatcher.dispatch(update);
    }
}
