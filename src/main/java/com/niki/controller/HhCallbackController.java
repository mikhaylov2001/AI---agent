package com.niki.controller;

import com.niki.service.HhOAuthService;
import com.niki.service.HhOAuthStateService;
import com.niki.service.NikiMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/hh")
@RequiredArgsConstructor
@Slf4j
public class HhCallbackController {

    private final HhOAuthService hhOAuthService;
    private final HhOAuthStateService stateService;
    private final NikiMessageSender messageSender;

  /** Прокси для Telegram: кнопка без underscore → редирект на hh.ru с правильными параметрами. */
    @GetMapping("/authorize")
    public RedirectView authorize(@RequestParam("s") String state) {
        stateService.decode(state);
        return new RedirectView(hhOAuthService.buildHhAuthorizeUrlWithState(state));
    }

    @GetMapping("/callback")
    public String callback(@RequestParam String code, @RequestParam String state) {
        try {
            Long telegramId = stateService.decode(state);
            hhOAuthService.exchangeCodeForTokens(telegramId, code);
            messageSender.sendMessage(telegramId,
                    "✅ *HH.ru подключён!*\n\n" +
                            "Теперь выбери резюме для откликов:\n/hh\\_resumes");
            return """
                    <html><body style='font-family:sans-serif;text-align:center;padding:40px'>
                    <h2>✅ Готово!</h2><p>Вернись в Telegram — Ники написал тебе.</p>
                    </body></html>
                    """;
        } catch (Exception e) {
            log.error("Ошибка HH callback: {}", e.getMessage());
            return "<html><body>Ошибка авторизации. Попробуй снова через /connect_hh</body></html>";
        }
    }
}
