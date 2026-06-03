package com.niki.service;

import com.niki.model.JobApplication;
import com.niki.model.JobApplication.ApplicationStatus;
import com.niki.model.User;
import com.niki.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository repository;

    @Transactional
    public JobApplication upsert(User user, HhService.VacancyDto vacancy, ApplicationStatus status) {
        JobApplication app = repository.findByUserTelegramIdAndVacancyId(user.getTelegramId(), vacancy.id())
                .orElseGet(() -> JobApplication.builder()
                        .user(user)
                        .vacancyId(vacancy.id())
                        .build());
        app.setVacancyTitle(vacancy.title());
        app.setCompany(vacancy.company());
        app.setVacancyUrl(vacancy.url());
        app.setStatus(status);
        if (vacancy.matchScore() != null) {
            app.setMatchScore(vacancy.matchScore());
        }
        return repository.save(app);
    }

    @Transactional
    public JobApplication saveLetter(User user, String vacancyId, String letter, Integer matchScore) {
        JobApplication app = repository.findByUserTelegramIdAndVacancyId(user.getTelegramId(), vacancyId)
                .orElseThrow(() -> new RuntimeException("Вакансия не найдена в трекере"));
        app.setCoverLetter(letter);
        app.setStatus(ApplicationStatus.LETTER_DRAFTED);
        if (matchScore != null) {
            app.setMatchScore(matchScore);
        }
        return repository.save(app);
    }

    @Transactional
    public JobApplication markApplied(User user, String vacancyId) {
        JobApplication app = repository.findByUserTelegramIdAndVacancyId(user.getTelegramId(), vacancyId)
                .orElseThrow(() -> new RuntimeException("Вакансия не найдена"));
        app.setStatus(ApplicationStatus.APPLIED);
        app.setAppliedAt(LocalDateTime.now());
        return repository.save(app);
    }

    @Transactional(readOnly = true)
    public List<JobApplication> listRecent(User user) {
        return repository.findByUserTelegramIdOrderByUpdatedAtDesc(user.getTelegramId());
    }

    @Transactional(readOnly = true)
    public String formatApplications(User user) {
        List<JobApplication> apps = listRecent(user);
        if (apps.isEmpty()) {
            return "📋 *Мои отклики*\n\nПока пусто. Найди вакансии через 💼 и нажми «Откликнуться».";
        }
        long applied = repository.countByUserTelegramIdAndStatus(user.getTelegramId(), ApplicationStatus.APPLIED);
        StringBuilder sb = new StringBuilder("📋 *Мои отклики* (всего: ")
                .append(apps.size()).append(", отправлено: ").append(applied).append(")\n\n");
        int limit = Math.min(apps.size(), 10);
        for (int i = 0; i < limit; i++) {
            JobApplication a = apps.get(i);
            sb.append(String.format("%d. %s *%s*\n   🏢 %s | %s",
                    i + 1, statusEmoji(a.getStatus()), a.getVacancyTitle(),
                    nullToDash(a.getCompany()), statusLabel(a.getStatus())));
            if (a.getMatchScore() != null) {
                sb.append(" | 🎯 ").append(a.getMatchScore()).append("%");
            }
            sb.append("\n");
            if (StringUtils.hasText(a.getVacancyUrl())) {
                sb.append("   🔗 ").append(a.getVacancyUrl()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String statusEmoji(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> "✅";
            case LETTER_DRAFTED -> "📝";
            case SAVED -> "💾";
            case SKIPPED -> "⏭";
            case INTERVIEW -> "🎤";
            case REJECTED -> "❌";
            default -> "👀";
        };
    }

    private static String statusLabel(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> "отправлен";
            case LETTER_DRAFTED -> "письмо готово";
            case SAVED -> "сохранена";
            case SKIPPED -> "пропущена";
            case INTERVIEW -> "собес";
            case REJECTED -> "отказ";
            default -> "просмотр";
        };
    }

    private static String nullToDash(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }
}
