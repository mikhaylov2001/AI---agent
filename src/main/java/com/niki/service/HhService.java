package com.niki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
public class HhService {

    private final WebClient hhClient = WebClient.builder()
            .baseUrl("https://api.hh.ru")
            .defaultHeader("User-Agent", "NikiBot/1.0 (nikibot@gmail.com)")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
            .build();

    @SuppressWarnings("unchecked")
    public List<VacancyDto> searchVacancies(String query, int areaId, int count) {
        try {
            Map<String, Object> response = hhClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/vacancies")
                            .queryParam("text", query)
                            .queryParam("area", areaId)
                            .queryParam("per_page", count)
                            .queryParam("order_by", "relevance")
                            .build())
                    .retrieve().bodyToMono(Map.class).block();
            if (response == null) {
                return List.of();
            }
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            List<VacancyDto> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                result.add(parseVacancy(item));
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка HH API: {}", e.getMessage());
            return List.of();
        }
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
        return new VacancyDto(title, company, salary, experience, url);
    }

    public String formatVacancies(List<VacancyDto> vacancies, String query) {
        if (vacancies.isEmpty()) {
            return "😔 По запросу *\"" + query + "\"* вакансий не найдено.";
        }
        StringBuilder sb = new StringBuilder("💼 Вакансии по запросу *\"" + query + "*\":\n\n");
        for (int i = 0; i < vacancies.size(); i++) {
            VacancyDto v = vacancies.get(i);
            sb.append(String.format("*%d. %s*\n🏢 %s\n💰 %s\n", i + 1, v.title(), v.company(), v.salary()));
            if (!v.experience().isBlank()) {
                sb.append("📋 ").append(v.experience()).append("\n");
            }
            sb.append("🔗 [Открыть на HH](").append(v.url()).append(")\n\n");
        }
        sb.append("_Нашёл подходящую? /apply [ссылка] — откликнусь с письмом!_");
        return sb.toString();
    }

    public record VacancyDto(String title, String company, String salary, String experience, String url) {
    }
}
