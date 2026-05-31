package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HhApplyService {

    private final HhOAuthService hhOAuthService;
    private final UserRepository userRepository;
    private final WebClient hhApiClient = WebClient.builder()
            .baseUrl("https://api.hh.ru")
            .defaultHeader("User-Agent", "NikiBot/1.0 (nikibot@gmail.com)")
            .build();

    @SuppressWarnings("unchecked")
    public String getMyResumes(User user) {
        if (!hhOAuthService.isConnected(user)) {
            return "❌ HH не подключён. Напиши /connect_hh";
        }
        String token = hhOAuthService.getValidToken(user);
        try {
            Map<String, Object> response = hhApiClient.get()
                    .uri("/resumes/mine")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null || items.isEmpty()) {
                return "У тебя нет резюме на HH. Создай на hh.ru";
            }
            StringBuilder sb = new StringBuilder("📄 *Твои резюме на HH:*\n\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(String.format("%d. *%s*\n`ID: %s`\n\n",
                        i + 1, items.get(i).get("title"), items.get(i).get("id")));
            }
            sb.append("Выбери резюме: /use\\_resume [ID]");
            return sb.toString();
        } catch (Exception e) {
            log.error("Ошибка резюме: {}", e.getMessage());
            return "Ошибка получения резюме 😅";
        }
    }

    @Transactional
    public String selectResume(User user, String resumeId) {
        user.setHhResumeId(resumeId);
        userRepository.save(user);
        return "✅ Резюме выбрано!\nТеперь: /apply [ссылка на вакансию]";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getVacancyDetails(String vacancyIdOrUrl) {
        String vacancyId = vacancyIdOrUrl
                .replaceAll(".*hh\\.ru/vacancy/(\\d+).*", "$1")
                .replaceAll(".*api\\.hh\\.ru/vacancies/(\\d+).*", "$1").trim();
        try {
            return hhApiClient.get().uri("/vacancies/" + vacancyId)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Ошибка вакансии {}: {}", vacancyId, e.getMessage());
            return null;
        }
    }

    public String applyToVacancy(User user, String vacancyIdOrUrl, String coverLetter) {
        if (!hhOAuthService.isConnected(user)) {
            return "❌ HH не подключён. Напиши /connect_hh";
        }
        if (user.getHhResumeId() == null) {
            return "❌ Выбери резюме: /hh\\_resumes";
        }
        String vacancyId = vacancyIdOrUrl.replaceAll(".*hh\\.ru/vacancy/(\\d+).*", "$1");
        String token = hhOAuthService.getValidToken(user);
        try {
            Map<String, String> body = new HashMap<>();
            body.put("vacancy_id", vacancyId);
            body.put("resume_id", user.getHhResumeId());
            body.put("message", coverLetter);
            hhApiClient.post().uri("/negotiations")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().toBodilessEntity().block();
            log.info("Отклик отправлен: user={}, vacancy={}", user.getTelegramId(), vacancyId);
            return "✅ *Отклик отправлен!*\n\nУдачи 🍀 Напиши как пройдёт — помогу подготовиться.";
        } catch (Exception e) {
            log.error("Ошибка отклика: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("403")) {
                return "❌ Ты уже откликался на эту вакансию.";
            }
            return "❌ Ошибка: " + e.getMessage();
        }
    }
}
