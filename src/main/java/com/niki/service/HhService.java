package com.niki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HhService {

    private final HhAppTokenService hhAppTokenService;

    @Value("${hh.user-agent:NikiBot/1.5 (babaykin35@gmail.com)}")
    private String userAgent;

    @Value("${hh.default-area:}")
    private String defaultArea;

    @Value("${hh.search-per-page:8}")
    private int searchPerPage;

    private WebClient hhClient() {
        return WebClient.builder()
                .baseUrl("https://api.hh.ru")
                .defaultHeader("User-Agent", userAgent)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    public VacancySearchResult searchVacancies(String query) {
        return searchVacancies(query, defaultArea, searchPerPage);
    }

    @SuppressWarnings("unchecked")
    public VacancySearchResult searchVacancies(String query, String area, int count) {
        String q = StringUtils.hasText(query) ? query.trim() : "Java backend";
        try {
            WebClient client = hhClient();
            var spec = client.get().uri(uriBuilder -> {
                var b = uriBuilder
                        .path("/vacancies")
                        .queryParam("text", q)
                        .queryParam("per_page", count)
                        .queryParam("order_by", "publication_time");
                if (StringUtils.hasText(area)) {
                    b.queryParam("area", area);
                }
                return b.build();
            });

            String appToken = hhAppTokenService.getAppToken();
            if (appToken != null) {
                spec = spec.header("Authorization", "Bearer " + appToken);
            }

            Map<String, Object> response = spec.retrieve().bodyToMono(Map.class).block();
            if (response == null) {
                return VacancySearchResult.error("Пустой ответ от HH.ru");
            }
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null || items.isEmpty()) {
                return VacancySearchResult.empty(q);
            }
            List<VacancyDto> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                result.add(parseVacancy(item));
            }
            return VacancySearchResult.ok(q, result);
        } catch (WebClientResponseException e) {
            log.error("HH API {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return mapHttpError(e, q);
        } catch (Exception e) {
            log.error("HH API: {}", e.getMessage());
            return VacancySearchResult.error("Ошибка поиска: " + e.getMessage());
        }
    }

    private VacancySearchResult mapHttpError(WebClientResponseException e, String query) {
        String body = e.getResponseBodyAsString();
        if (body.contains("bad_user_agent")) {
            return VacancySearchResult.error(
                    "HH отклонил запрос (User-Agent). Задай HH_USER_AGENT на Render или дождись HH_CLIENT_ID/SECRET.");
        }
        if (e.getStatusCode().value() == 403) {
            if (hhAppTokenService.isConfigured()) {
                return VacancySearchResult.error(
                        "HH 403: проверь Client ID/Secret или дождись одобрения приложения на dev.hh.ru.");
            }
            return VacancySearchResult.error(
                    "HH требует авторизацию приложения. Добавь HH_CLIENT_ID и HH_CLIENT_SECRET на Render (после одобрения заявки).");
        }
        return VacancySearchResult.error("HH ошибка " + e.getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private VacancyDto parseVacancy(Map<String, Object> item) {
        String title = (String) item.getOrDefault("name", "Без названия");
        String url = (String) item.getOrDefault("alternate_url", "");
        String company = "Не указан";
        Map<String, Object> employer = (Map<String, Object>) item.get("employer");
        if (employer != null) {
            company = (String) employer.getOrDefault("name", "Не указан");
        }
        String salary = "Зарплата не указана";
        Map<String, Object> salaryObj = (Map<String, Object>) item.get("salary");
        if (salaryObj != null) {
            Object from = salaryObj.get("from");
            Object to = salaryObj.get("to");
            String cur = (String) salaryObj.getOrDefault("currency", "RUR");
            if (from != null && to != null) {
                salary = from + "–" + to + " " + cur;
            } else if (from != null) {
                salary = "от " + from + " " + cur;
            } else if (to != null) {
                salary = "до " + to + " " + cur;
            }
        }
        String experience = "";
        Map<String, Object> exp = (Map<String, Object>) item.get("experience");
        if (exp != null) {
            experience = (String) exp.getOrDefault("name", "");
        }
        String area = "";
        Map<String, Object> areaObj = (Map<String, Object>) item.get("area");
        if (areaObj != null) {
            area = (String) areaObj.getOrDefault("name", "");
        }
        return new VacancyDto(title, company, salary, experience, area, url);
    }

    public String formatSearchResult(VacancySearchResult result) {
        if (result.error() != null) {
            return "⚠️ " + result.error();
        }
        if (result.vacancies().isEmpty()) {
            return "😔 По запросу *\"" + result.query() + "\"* вакансий не найдено.\n\nПопробуй /job\\_query Spring Boot";
        }
        StringBuilder sb = new StringBuilder("💼 Вакансии: *\"" + result.query() + "*\" (" + result.vacancies().size() + ")\n\n");
        for (int i = 0; i < result.vacancies().size(); i++) {
            VacancyDto v = result.vacancies().get(i);
            sb.append(String.format("*%d. %s*\n🏢 %s\n💰 %s\n", i + 1, v.title(), v.company(), v.salary()));
            if (!v.area().isBlank()) {
                sb.append("📍 ").append(v.area()).append("\n");
            }
            if (!v.experience().isBlank()) {
                sb.append("📋 ").append(v.experience()).append("\n");
            }
            sb.append("🔗 ").append(v.url()).append("\n\n");
        }
        sb.append("_Письмо: /apply [ссылка]_");
        return sb.toString();
    }

    public record VacancyDto(String title, String company, String salary, String experience, String area, String url) {
    }

    public record VacancySearchResult(String query, List<VacancyDto> vacancies, String error) {
        static VacancySearchResult ok(String query, List<VacancyDto> vacancies) {
            return new VacancySearchResult(query, vacancies, null);
        }

        static VacancySearchResult empty(String query) {
            return new VacancySearchResult(query, List.of(), null);
        }

        static VacancySearchResult error(String error) {
            return new VacancySearchResult("", List.of(), error);
        }
    }
}
