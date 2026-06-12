package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@Slf4j
public class HhApplyService {

    public static final String MSG_NOT_CONNECTED = "❌ HH не подключён. Напиши /connect\\_hh";
    public static final String MSG_SESSION_EXPIRED = "❌ HH-сессия истекла. Нажми /connect\\_hh — подключишь за минуту.";

    private final HhOAuthService hhOAuthService;
    private final UserRepository userRepository;
    private final WebClient hhApiWebClient;

    public HhApplyService(HhOAuthService hhOAuthService,
                          UserRepository userRepository,
                          @Qualifier("hhApiWebClient") WebClient hhApiWebClient) {
        this.hhOAuthService = hhOAuthService;
        this.userRepository = userRepository;
        this.hhApiWebClient = hhApiWebClient;
    }

    @SuppressWarnings("unchecked")
    public String getMyResumes(User user) {
        if (!hhOAuthService.isConnected(user)) {
            return MSG_NOT_CONNECTED;
        }
        return fetchMyResumesText(user, false);
    }

    @SuppressWarnings("unchecked")
    private String fetchMyResumesText(User user, boolean retriedAfter401) {
        try {
            String token = hhOAuthService.getValidToken(user);
            Map<String, Object> response = hhApiWebClient.get()
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
                sb.append(String.format("%d. *%s*\nID: %s\n\n",
                        i + 1, items.get(i).get("title"), items.get(i).get("id")));
            }
            sb.append("Нажми кнопку под сообщением или: /use\\_resume [ID]");
            return sb.toString();
        } catch (HhAuthException e) {
            log.warn("getMyResumes auth: {}", e.getMessage());
            return MSG_SESSION_EXPIRED;
        } catch (WebClientResponseException e) {
            log.error("Ошибка резюме {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) && !retriedAfter401) {
                try {
                    hhOAuthService.forceRefreshAccessToken(user);
                    return fetchMyResumesText(user, true);
                } catch (HhAuthException refreshError) {
                    return MSG_SESSION_EXPIRED;
                }
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return MSG_SESSION_EXPIRED;
            }
            return "Ошибка HH (" + e.getStatusCode().value() + "). Попробуй /connect\\_hh";
        } catch (Exception e) {
            log.error("Ошибка резюме: {}", e.getMessage(), e);
            return "Ошибка получения резюме 😅\nПроверь /connect\\_hh или попробуй позже.";
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> listResumes(User user) {
        if (!hhOAuthService.isConnected(user)) {
            return List.of();
        }
        return fetchResumeList(user, false);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchResumeList(User user, boolean retriedAfter401) {
        try {
            String token = hhOAuthService.getValidToken(user);
            Map<String, Object> response = hhApiWebClient.get()
                    .uri("/resumes/mine")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) {
                return List.of();
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                result.add(Map.of(
                        "id", String.valueOf(item.get("id")),
                        "title", String.valueOf(item.getOrDefault("title", "Резюме"))
                ));
            }
            return result;
        } catch (HhAuthException e) {
            return List.of();
        } catch (WebClientResponseException e) {
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) && !retriedAfter401) {
                try {
                    hhOAuthService.forceRefreshAccessToken(user);
                    return fetchResumeList(user, true);
                } catch (HhAuthException ignored) {
                    return List.of();
                }
            }
            log.error("listResumes {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("listResumes: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean needsReconnect(String message) {
        return MSG_NOT_CONNECTED.equals(message) || MSG_SESSION_EXPIRED.equals(message);
    }

    @Transactional
    public String selectResume(User user, String resumeId) {
        user.setHhResumeId(resumeId);
        userRepository.save(user);
        return "✅ Резюме выбрано!\nТеперь жми «Откликнуться» под вакансией или /apply [ссылка]";
    }

    @SuppressWarnings("unchecked")
    public String getResumeSummary(User user) {
        if (!StringUtils.hasText(user.getHhResumeId()) || !hhOAuthService.isConnected(user)) {
            return "";
        }
        try {
            String token = hhOAuthService.getValidToken(user);
            Map<String, Object> resume = hhApiWebClient.get()
                    .uri("/resumes/" + user.getHhResumeId())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resume == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Должность: ").append(resume.getOrDefault("title", "")).append("\n");
            if (resume.get("skill_set") instanceof List<?> skills) {
                sb.append("Навыки: ").append(String.join(", ", skills.stream().map(String::valueOf).toList())).append("\n");
            }
            if (resume.get("experience") instanceof List<?> exp && !exp.isEmpty()) {
                sb.append("Опыт:\n");
                for (Object o : exp) {
                    if (o instanceof Map<?, ?> m) {
                        sb.append("- ").append(String.valueOf(m.get("position")))
                                .append(" @ ").append(String.valueOf(m.get("company"))).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("getResumeSummary: {}", e.getMessage());
            return "";
        }
    }

    public String extractVacancyId(String vacancyIdOrUrl) {
        return vacancyIdOrUrl
                .replaceAll(".*hh\\.ru/vacancy/(\\d+).*", "$1")
                .replaceAll(".*api\\.hh\\.ru/vacancies/(\\d+).*", "$1")
                .trim();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getVacancyDetails(String vacancyIdOrUrl) {
        String vacancyId = extractVacancyId(vacancyIdOrUrl);
        try {
            return hhApiWebClient.get().uri("/vacancies/" + vacancyId)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Ошибка вакансии {}: {}", vacancyId, e.getMessage());
            return null;
        }
    }

    public String plainDescription(Map<String, Object> vacancy) {
        if (vacancy == null || vacancy.get("description") == null) {
            return "";
        }
        String raw = vacancy.get("description").toString().replaceAll("<[^>]+>", " ");
        return raw.length() > 800 ? raw.substring(0, 800) : raw;
    }

    public String applyToVacancy(User user, String vacancyIdOrUrl, String coverLetter) {
        return applyToVacancy(user, vacancyIdOrUrl, coverLetter, false);
    }

    private String applyToVacancy(User user, String vacancyIdOrUrl, String coverLetter, boolean retriedAfter401) {
        if (!hhOAuthService.isConnected(user)) {
            return MSG_NOT_CONNECTED;
        }
        if (user.getHhResumeId() == null) {
            return "❌ Выбери резюме: /hh\\_resumes";
        }
        String vacancyId = extractVacancyId(vacancyIdOrUrl);
        try {
            String token = hhOAuthService.getValidToken(user);
            Map<String, String> body = new HashMap<>();
            body.put("vacancy_id", vacancyId);
            body.put("resume_id", user.getHhResumeId());
            body.put("message", coverLetter);
            hhApiWebClient.post().uri("/negotiations")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().toBodilessEntity().block();
            log.info("Отклик отправлен: user={}, vacancy={}", user.getTelegramId(), vacancyId);
            return "✅ *Отклик отправлен!*\n\nУдачи 🍀 Напиши как пройдёт — помогу подготовиться.";
        } catch (HhAuthException e) {
            return MSG_SESSION_EXPIRED;
        } catch (WebClientResponseException e) {
            log.error("Ошибка отклика {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401 && !retriedAfter401) {
                try {
                    hhOAuthService.forceRefreshAccessToken(user);
                    return applyToVacancy(user, vacancyIdOrUrl, coverLetter, true);
                } catch (HhAuthException refreshError) {
                    return MSG_SESSION_EXPIRED;
                }
            }
            if (e.getStatusCode().value() == 403) {
                return "❌ Ты уже откликался на эту вакансию или нет доступа.";
            }
            if (e.getStatusCode().value() == 401) {
                return MSG_SESSION_EXPIRED;
            }
            return "❌ Ошибка HH: " + e.getStatusCode().value();
        } catch (Exception e) {
            log.error("Ошибка отклика: {}", e.getMessage());
            return "❌ Ошибка: " + e.getMessage();
        }
    }
}
