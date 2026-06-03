package com.niki.service;

import com.niki.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@Slf4j
public class HhService {

    private final HhAppTokenService hhAppTokenService;
    private final WebClient hhApiWebClient;

    @Value("${hh.default-area:}")
    private String defaultArea;

    @Value("${hh.search-per-page:8}")
    private int searchPerPage;

    public int getSearchPerPage() {
        return searchPerPage;
    }

    public HhService(HhAppTokenService hhAppTokenService,
                     @Qualifier("hhApiWebClient") WebClient hhApiWebClient) {
        this.hhAppTokenService = hhAppTokenService;
        this.hhApiWebClient = hhApiWebClient;
    }

    public VacancySearchResult searchVacancies(String query) {
        return searchVacancies(HhSearchFilters.defaults(query, defaultArea, searchPerPage));
    }

    public VacancySearchResult searchVacancies(User user, String query) {
        return searchVacancies(HhSearchFilters.fromUser(user, query, defaultArea, searchPerPage));
    }

    public VacancySearchResult searchVacancies(String query, String area, int count) {
        return searchVacancies(new HhSearchFilters(
                StringUtils.hasText(query) ? query : "Java backend developer",
                area, null, false, false, count));
    }

    @SuppressWarnings("unchecked")
    public VacancySearchResult searchVacancies(HhSearchFilters filters) {
        String q = filters.query();
        try {
            var spec = hhApiWebClient.get().uri(uriBuilder -> {
                var b = uriBuilder
                        .path("/vacancies")
                        .queryParam("text", q)
                        .queryParam("per_page", filters.perPage())
                        .queryParam("order_by", "publication_time");
                if (StringUtils.hasText(filters.area())) {
                    b.queryParam("area", filters.area());
                }
                if (StringUtils.hasText(filters.experience())) {
                    b.queryParam("experience", filters.experience());
                }
                if (filters.remote()) {
                    b.queryParam("schedule", "remote");
                }
                if (filters.onlyWithSalary()) {
                    b.queryParam("only_with_salary", "true");
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
                return VacancySearchResult.empty(q, filters);
            }
            List<VacancyDto> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                result.add(parseVacancy(item));
            }
            return VacancySearchResult.ok(q, filters, result);
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
                    "HH требует авторизацию приложения. Добавь HH_CLIENT_ID и HH_CLIENT_SECRET на Render.");
        }
        return VacancySearchResult.error("HH ошибка " + e.getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private VacancyDto parseVacancy(Map<String, Object> item) {
        String id = String.valueOf(item.get("id"));
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
        return new VacancyDto(id, title, company, salary, experience, area, url, null);
    }

    public VacancyDto withMatchScore(VacancyDto v, Integer score) {
        return new VacancyDto(v.id(), v.title(), v.company(), v.salary(), v.experience(), v.area(), v.url(), score);
    }

    public String formatSearchResult(VacancySearchResult result) {
        if (result.error() != null) {
            return "⚠️ " + result.error();
        }
        if (result.vacancies().isEmpty()) {
            return "😔 По запросу *\"" + result.query() + "\"* вакансий не найдено.\n\nПопробуй другой фильтр или /job\\_query";
        }
        String filterHint = formatFilterHint(result.filters());
        StringBuilder sb = new StringBuilder("💼 *Вакансии:* \"" + result.query() + "\" (" + result.vacancies().size() + ")\n");
        if (!filterHint.isBlank()) {
            sb.append(filterHint).append("\n");
        }
        sb.append("\n");
        for (int i = 0; i < result.vacancies().size(); i++) {
            VacancyDto v = result.vacancies().get(i);
            sb.append(String.format("*%d. %s*\n🏢 %s\n💰 %s\n", i + 1, v.title(), v.company(), v.salary()));
            if (!v.area().isBlank()) {
                sb.append("📍 ").append(v.area()).append("\n");
            }
            if (!v.experience().isBlank()) {
                sb.append("📋 ").append(v.experience()).append("\n");
            }
            if (v.matchScore() != null) {
                sb.append("🎯 Match: ").append(v.matchScore()).append("%\n");
            }
            sb.append("🔗 ").append(v.url()).append("\n\n");
        }
        sb.append("_Кнопки под сообщением: Откликнуться / Сохранить / Пропустить_");
        return sb.toString();
    }

    private static String formatFilterHint(HhSearchFilters f) {
        if (f == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(f.area())) {
            parts.add("регион " + f.area());
        }
        if (f.remote()) {
            parts.add("удалёнка");
        }
        if (StringUtils.hasText(f.experience())) {
            parts.add("опыт: " + f.experience());
        }
        return parts.isEmpty() ? "" : "Фильтры: " + String.join(", ", parts);
    }

    public record VacancyDto(
            String id, String title, String company, String salary,
            String experience, String area, String url, Integer matchScore) {
    }

    public record VacancySearchResult(
            String query, HhSearchFilters filters, List<VacancyDto> vacancies, String error) {

        static VacancySearchResult ok(String query, HhSearchFilters filters, List<VacancyDto> vacancies) {
            return new VacancySearchResult(query, filters, vacancies, null);
        }

        static VacancySearchResult empty(String query, HhSearchFilters filters) {
            return new VacancySearchResult(query, filters, List.of(), null);
        }

        static VacancySearchResult error(String error) {
            return new VacancySearchResult("", null, List.of(), error);
        }
    }
}
