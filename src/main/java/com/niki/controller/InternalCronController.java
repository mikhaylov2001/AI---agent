package com.niki.controller;

import com.niki.service.ProactiveAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Внешний cron (UptimeRobot и т.п.) будит задачи на Render Free tier.
 * Настрой GET/POST каждый час на /internal/cron с заголовком X-Cron-Secret.
 */
@RestController
@RequiredArgsConstructor
public class InternalCronController {

    private final ProactiveAgentService proactiveAgentService;

    @Value("${niki.cron-secret:}")
    private String cronSecret;

    @PostMapping("/internal/cron")
    public ResponseEntity<Map<String, Object>> cron(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (!StringUtils.hasText(cronSecret) || !cronSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "invalid cron secret"));
        }
        Map<String, Object> result = proactiveAgentService.runDueTasks();
        return ResponseEntity.ok(result);
    }
}
