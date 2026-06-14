package com.niki.service;

import com.niki.bot.TelegramKeyboards;
import com.niki.model.JobApplication.ApplicationStatus;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int MAX_AUTO_APPLY_PER_RUN = 2;

    private final UserRepository userRepository;
    private final HhService hhService;
    private final JobApplicationService jobApplicationService;
    private final VacancyApplyOrchestrator vacancyApplyOrchestrator;
    private final HhOAuthService hhOAuthService;

    @Value("${telegram.owner.id:0}")
    private long ownerTelegramId;

    private NikiMessageSender messageSender;

    public void setMessageSender(NikiMessageSender sender) {
        this.messageSender = sender;
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
        List<HhService.VacancyDto> manualApply = new ArrayList<>();
        int autoApplied = 0;

        for (int i = 0; i < fresh.size(); i++) {
            HhService.VacancyDto v = fresh.get(i);
            seen.add(v.id());

            if (autoApplied < MAX_AUTO_APPLY_PER_RUN && isAutoApplyOn(user)) {
                Optional<VacancyApplyOrchestrator.ApplyResult> applied =
                        vacancyApplyOrchestrator.tryAutoApply(user, v);
                if (applied.isPresent() && applied.get().applied()) {
                    autoApplied++;
                    msg.append(String.format("%d. ✅ *%s* — авто-отклик (%d%%)\n🏢 %s\n🔗 %s\n\n",
                            i + 1, v.title(), applied.get().matchScore(), v.company(), v.url()));
                    continue;
                }
            }

            msg.append(String.format("%d. *%s*\n🏢 %s | 💰 %s\n🔗 %s\n\n",
                    i + 1, v.title(), v.company(), v.salary(), v.url()));
            jobApplicationService.upsert(user, v, ApplicationStatus.SEEN);
            manualApply.add(v);
        }

        if (autoApplied > 0) {
            msg.append("_Авто-откликов: ").append(autoApplied).append("_\n");
        }
        if (!manualApply.isEmpty()) {
            msg.append("_Остальные — кнопки ниже или /applications_");
        } else if (autoApplied > 0) {
            msg.append("_Смотри статус в 📋 Отклики_");
        } else if (isAutoApplyOn(user) && !VacancyApplyOrchestrator.isReadyForApply(user, hhOAuthService)) {
            sendSetupHintIfNeeded(user);
        }

        user.setLastNotifiedVacancies(capSeenIds(seen));
        user.setLastJobAlertAt(LocalDateTime.now());
        userRepository.save(user);

        if (!manualApply.isEmpty()) {
            messageSender.sendMessageWithInline(user.getTelegramId(), msg.toString(),
                    TelegramKeyboards.vacancyActions(manualApply));
        } else {
            messageSender.sendMessage(user.getTelegramId(), msg.toString());
        }
    }

    private void sendSetupHintIfNeeded(User user) {
        if (!hhOAuthService.isConnected(user)) {
            send(user.getTelegramId(),
                    "⚠️ Для авто-откликов подключи HH: /connect\\_hh и выбери резюме в 🧠 Профиль.");
        } else if (!StringUtils.hasText(user.getHhResumeId())) {
            send(user.getTelegramId(), "⚠️ Выбери резюме: 🧠 Профиль → 📄 Резюме");
        }
    }

    @Transactional
    public String setAutopilot(User user, boolean enabled) {
        user.setProactiveEnabled(enabled);
        user.setOnboardingDone(true);
        userRepository.save(user);
        if (enabled) {
            user.setJobAlertsEnabled(true);
            userRepository.save(user);
        }
        return enabled
                ? """
                ✅ *Авто-отклики включены*
                
                Буду искать вакансии 2 раза в день и откликаться сам (match ≥ 50%%), если подключён HH и выбрано резюме.
                Запрос: `%s`
                """.formatted(jobQuery(user)).trim()
                : "⏸ Авто-отклики выключены. Алерты вакансий не трогал — /job\\_alerts off";
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
                ⚙️ *Автоматизация*
                
                Авто-отклики (match ≥ 50%%): %s
                Алерты вакансий: %s
                Запрос: `%s`
                HH: %s · Резюме: %s
                
                /autopilot on|off — авто-отклики
                /job_alerts on|off — только алерты
                /job_query Java backend
                """,
                isAutoApplyOn(user) ? "✅ вкл" : "❌ выкл",
                isJobAlertsOn(user) ? "✅ вкл" : "❌ выкл",
                jobQuery(user),
                hhOAuthService.isConnected(user) ? "✅" : "❌ /connect_hh",
                StringUtils.hasText(user.getHhResumeId()) ? "✅" : "❌ в профиле");
    }

    static boolean isAutoApplyOn(User user) {
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

    private void send(Long chatId, String text) {
        messageSender.sendMessage(chatId, text);
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
