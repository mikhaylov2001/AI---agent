package com.niki.service;

import com.niki.bot.TelegramKeyboards;
import com.niki.model.Goal;
import com.niki.model.JobApplication.ApplicationStatus;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProactiveAgentService {

    private static final int MAX_SEEN_VACANCIES = 200;

    private final UserRepository userRepository;
    private final GoalService goalService;
    private final LlmService llmService;
    private final HhService hhService;
    private final JobApplicationService jobApplicationService;

    private NikiMessageSender messageSender;

    public void setMessageSender(NikiMessageSender sender) {
        this.messageSender = sender;
    }

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

    @Scheduled(cron = "${niki.midday-cron}")
    public void middayCheckIn() {
        runForProactiveUsers(user -> {
            String text = llmService.proactiveBrief(user, goalService.getActiveGoals(user.getTelegramId()),
                    "Дневной чек-ин. Спроси энергию 1-10 и предложи один шаг до вечера.");
            send(user.getTelegramId(), "📊 *Чек-ин дня*\n\n" + text);
        });
    }

    @Scheduled(cron = "${niki.evening-cron}")
    public void eveningRecap() {
        runForProactiveUsers(user -> {
            String text = llmService.proactiveBrief(user, goalService.getActiveGoals(user.getTelegramId()),
                    "Вечерний итог. Спроси что сделано сегодня и один шаг на завтра.");
            send(user.getTelegramId(), "🌙 *Вечер*\n\n" + text);
        });
    }

    @Scheduled(cron = "${niki.job-alert-cron}")
    @Transactional
    public void jobAlerts() {
        runJobAlertsInternal();
    }

    /** Для внешнего cron на Render Free — запускает задачи по текущему UTC-часу. */
    public Map<String, Object> runDueTasks() {
        int hour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        Map<String, Object> ran = new LinkedHashMap<>();
        if (hour == 6) {
            morningBrief();
            ran.put("morning", true);
        }
        if (hour == 11) {
            middayCheckIn();
            ran.put("midday", true);
        }
        if (hour == 18) {
            eveningRecap();
            ran.put("evening", true);
        }
        if (hour == 7 || hour == 15) {
            runJobAlertsInternal();
            ran.put("jobAlerts", true);
        }
        if (ran.isEmpty()) {
            ran.put("skipped", "no tasks for UTC hour " + hour);
        }
        return ran;
    }

    private void runJobAlertsInternal() {
        if (messageSender == null) {
            return;
        }
        log.info("Проактивный поиск вакансий...");
        for (User user : userRepository.findByJobAlertsEnabledTrue()) {
            try {
                notifyNewVacancies(user);
            } catch (Exception e) {
                log.error("Job alert для {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void notifyNewVacancies(User user) {
        HhService.VacancySearchResult search = hhService.searchVacancies(user, user.getJobSearchQuery());
        if (search.error() != null || search.vacancies().isEmpty()) {
            if (search.error() != null) {
                log.warn("Job alert skip: {}", search.error());
            }
            return;
        }

        Set<String> seen = parseSeenIds(user.getLastNotifiedVacancies());
        List<HhService.VacancyDto> fresh = search.vacancies().stream()
                .filter(v -> !seen.contains(v.id()))
                .limit(3)
                .toList();

        if (fresh.isEmpty()) {
            return;
        }

        StringBuilder msg = new StringBuilder("💼 *Новые вакансии* (").append(search.query()).append("):\n\n");
        for (int i = 0; i < fresh.size(); i++) {
            HhService.VacancyDto v = fresh.get(i);
            msg.append(String.format("%d. *%s*\n🏢 %s | 💰 %s\n🔗 %s\n\n",
                    i + 1, v.title(), v.company(), v.salary(), v.url()));
            seen.add(v.id());
            jobApplicationService.upsert(user, v, ApplicationStatus.SEEN);
        }
        msg.append("_Кнопки под сообщением — откликнуться в 1 клик_");

        user.setLastNotifiedVacancies(capSeenIds(seen));
        user.setLastJobAlertAt(LocalDateTime.now());
        userRepository.save(user);

        messageSender.sendMessageWithInline(user.getTelegramId(), msg.toString(),
                TelegramKeyboards.vacancyActions(fresh));
    }

    @Transactional
    public String setAutopilot(User user, boolean enabled) {
        user.setProactiveEnabled(enabled);
        user.setOnboardingDone(true);
        userRepository.save(user);
        return enabled
                ? "✅ *Автопилот включён*\n\nЯ буду писать сам: утро 9:00, день 14:00, вечер 21:00 (MSK).\nНа Render Free используй /internal/cron + UptimeRobot."
                : "⏸ Автопилот выключен. Пиши сам, когда нужен.";
    }

    @Transactional
    public String setJobAlerts(User user, boolean enabled) {
        user.setJobAlertsEnabled(enabled);
        user.setOnboardingDone(true);
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
        for (User user : userRepository.findByProactiveEnabledTrue()) {
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

    static boolean isProactiveOn(User user) {
        return Boolean.TRUE.equals(user.getProactiveEnabled());
    }

    static boolean isJobAlertsOn(User user) {
        return Boolean.TRUE.equals(user.getJobAlertsEnabled());
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String capSeenIds(Set<String> seen) {
        if (seen.size() <= MAX_SEEN_VACANCIES) {
            return String.join(",", seen);
        }
        List<String> list = new ArrayList<>(seen);
        return String.join(",", list.subList(list.size() - MAX_SEEN_VACANCIES, list.size()));
    }
}
