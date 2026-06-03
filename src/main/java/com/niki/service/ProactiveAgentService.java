package com.niki.service;

import com.niki.model.Goal;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Проактивная работа агента — пишет сам по расписанию, без сообщения пользователя.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProactiveAgentService {

    private final UserRepository userRepository;
    private final GoalService goalService;
    private final LlmService llmService;
    private final HhService hhService;

    private NikiMessageSender messageSender;

    public void setMessageSender(NikiMessageSender sender) {
        this.messageSender = sender;
    }

    /** 09:00 MSK — утренний бриф + следующий шаг от ИИ */
    @Scheduled(cron = "${niki.morning-cron}")
    public void morningBrief() {
        runForProactiveUsers(user -> {
            List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
            String goalsHint = goals.isEmpty()
                    ? "целей пока нет — предложи одну маленькую цель на сегодня"
                    : "главная цель: " + goals.get(0).getTitle();
            String text = llmService.proactiveBrief(user, goals,
                    "Утренний бриф. " + goalsHint + ". Коротко: что вижу, один шаг на сегодня. Без воды.");
            send(user.getTelegramId(), "☀️ *Доброе утро!*\n\n" + text);
        });
    }

    /** 14:00 MSK — мягкий чек-ин днём */
    @Scheduled(cron = "${niki.midday-cron}")
    public void middayCheckIn() {
        runForProactiveUsers(user -> {
            String text = llmService.proactiveBrief(user, goalService.getActiveGoals(user.getTelegramId()),
                    "Дневной чек-ин. Спроси энергию 1-10 и предложи один шаг до вечера.");
            send(user.getTelegramId(), "📊 *Чек-ин дня*\n\n" + text);
        });
    }

    /** 21:00 MSK — вечерний итог */
    @Scheduled(cron = "${niki.evening-cron}")
    public void eveningRecap() {
        runForProactiveUsers(user -> {
            String text = llmService.proactiveBrief(user, goalService.getActiveGoals(user.getTelegramId()),
                    "Вечерний итог. Спроси что сделано сегодня и один шаг на завтра.");
            send(user.getTelegramId(), "🌙 *Вечер*\n\n" + text);
        });
    }

    /** 10:00 и 18:00 MSK — новые вакансии Java */
    @Scheduled(cron = "${niki.job-alert-cron}")
    @Transactional
    public void jobAlerts() {
        if (messageSender == null) {
            return;
        }
        log.info("Проактивный поиск вакансий...");
        for (User user : userRepository.findAll()) {
            if (!isJobAlertsOn(user)) {
                continue;
            }
            try {
                notifyNewVacancies(user);
            } catch (Exception e) {
                log.error("Job alert для {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void notifyNewVacancies(User user) {
        String query = StringUtils.hasText(user.getJobSearchQuery())
                ? user.getJobSearchQuery()
                : "Java backend developer";
        HhService.VacancySearchResult search = hhService.searchVacancies(query);
        if (search.error() != null || search.vacancies().isEmpty()) {
            if (search.error() != null) {
                log.warn("Job alert skip: {}", search.error());
            }
            return;
        }
        List<HhService.VacancyDto> vacancies = search.vacancies();

        Set<String> seen = parseSeenIds(user.getLastNotifiedVacancies());
        List<HhService.VacancyDto> fresh = vacancies.stream()
                .filter(v -> {
                    String id = extractVacancyId(v.url());
                    return id != null && !seen.contains(id);
                })
                .limit(3)
                .toList();

        if (fresh.isEmpty()) {
            return;
        }

        StringBuilder msg = new StringBuilder("💼 *Новые вакансии* (").append(query).append("):\n\n");
        for (int i = 0; i < fresh.size(); i++) {
            HhService.VacancyDto v = fresh.get(i);
            msg.append(String.format("%d. *%s*\n🏢 %s | 💰 %s\n🔗 %s\n\n",
                    i + 1, v.title(), v.company(), v.salary(), v.url()));
            String id = extractVacancyId(v.url());
            if (id != null) {
                seen.add(id);
            }
        }
        msg.append("_Отклик: /apply [ссылка]_");

        user.setLastNotifiedVacancies(String.join(",", seen));
        user.setLastJobAlertAt(LocalDateTime.now());
        userRepository.save(user);

        send(user.getTelegramId(), msg.toString());
    }

    @Transactional
    public String setAutopilot(User user, boolean enabled) {
        user.setProactiveEnabled(enabled);
        userRepository.save(user);
        return enabled
                ? "✅ *Автопилот включён*\n\nЯ буду писать сам: утро 9:00, день 14:00, вечер 21:00 (MSK)."
                : "⏸ Автопилот выключен. Пиши сам, когда нужен.";
    }

    @Transactional
    public String setJobAlerts(User user, boolean enabled) {
        user.setJobAlertsEnabled(enabled);
        userRepository.save(user);
        return enabled
                ? "✅ *Алерты вакансий включены*\n\nПроверка 2 раза в день. Запрос: `" + jobQuery(user) + "`"
                : "⏸ Алерты вакансий выключены.";
    }

    @Transactional
    public String setJobQuery(User user, String query) {
        user.setJobSearchQuery(query.trim());
        user.setJobAlertsEnabled(true);
        userRepository.save(user);
        return "✅ Буду искать: *" + query.trim() + "*\nАлерты включены.";
    }

    public String autopilotStatus(User user) {
        return String.format("""
                ⚙️ *Автопилот*
                
                Проактивные сообщения: %s
                Алерты вакансий: %s
                Запрос вакансий: `%s`
                
                /autopilot on|off
                /job_alerts on|off
                /job_query Java backend
                """,
                isProactiveOn(user) ? "✅ вкл" : "❌ выкл",
                isJobAlertsOn(user) ? "✅ вкл" : "❌ выкл",
                jobQuery(user));
    }

    private void runForProactiveUsers(java.util.function.Consumer<User> action) {
        if (messageSender == null) {
            return;
        }
        log.info("Проактивный цикл...");
        for (User user : userRepository.findAll()) {
            if (!isProactiveOn(user)) {
                continue;
            }
            try {
                action.accept(user);
            } catch (Exception e) {
                log.error("Proactive для {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }

    private void send(Long chatId, String text) {
        messageSender.sendMessage(chatId, text);
    }

    private static boolean isProactiveOn(User user) {
        return user.getProactiveEnabled() == null || Boolean.TRUE.equals(user.getProactiveEnabled());
    }

    private static boolean isJobAlertsOn(User user) {
        return user.getJobAlertsEnabled() == null || Boolean.TRUE.equals(user.getJobAlertsEnabled());
    }

    private static String jobQuery(User user) {
        return StringUtils.hasText(user.getJobSearchQuery())
                ? user.getJobSearchQuery()
                : "Java backend";
    }

    private static Set<String> parseSeenIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String extractVacancyId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        var m = java.util.regex.Pattern.compile("vacancy/(\\d+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
