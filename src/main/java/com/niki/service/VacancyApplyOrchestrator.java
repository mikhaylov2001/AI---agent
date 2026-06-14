package com.niki.service;

import com.niki.model.JobApplication.ApplicationStatus;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VacancyApplyOrchestrator {

    private static final int DEFAULT_MIN_SCORE = 50;

    private final HhApplyService hhApplyService;
    private final HhOAuthService hhOAuthService;
    private final LlmService llmService;
    private final JobApplicationService jobApplicationService;
    private final UserRepository userRepository;

    public record ApplyResult(int matchScore, String letter, String hhResult, boolean applied) {
    }

    static boolean isReadyForApply(User user, HhOAuthService hhOAuthService) {
        return Boolean.TRUE.equals(user.getProactiveEnabled())
                && hhOAuthService.isConnected(user)
                && StringUtils.hasText(user.getHhResumeId());
    }

    @Transactional
    public Optional<ApplyResult> tryAutoApply(User user, HhService.VacancyDto vacancy, int minScore) {
        if (!isReadyForApply(user, hhOAuthService)) {
            return Optional.empty();
        }
        Map<String, Object> details = hhApplyService.getVacancyDetails(vacancy.id());
        if (details == null) {
            return Optional.empty();
        }
        String description = hhApplyService.plainDescription(details);
        int matchScore = llmService.scoreVacancyMatch(user, vacancy.title(), description);
        if (matchScore < minScore) {
            jobApplicationService.upsert(user, withScore(vacancy, matchScore), ApplicationStatus.SEEN);
            return Optional.empty();
        }
        String letter = llmService.generateCoverLetter(
                user, vacancy.title(), description, hhApplyService.getResumeSummary(user), matchScore);
        HhService.VacancyDto scored = withScore(vacancy, matchScore);
        jobApplicationService.upsert(user, scored, ApplicationStatus.LETTER_DRAFTED);
        jobApplicationService.saveLetter(user, vacancy.id(), letter, matchScore);
        user.setLastCoverLetter(letter);
        user.setLastCoverVacancyId(vacancy.id());
        userRepository.save(user);

        String result = hhApplyService.applyToVacancy(user, vacancy.id(), letter);
        boolean applied = result.startsWith("✅");
        if (applied) {
            jobApplicationService.markApplied(user, vacancy.id());
        }
        return Optional.of(new ApplyResult(matchScore, letter, result, applied));
    }

    public Optional<ApplyResult> tryAutoApply(User user, HhService.VacancyDto vacancy) {
        return tryAutoApply(user, vacancy, DEFAULT_MIN_SCORE);
    }

    private static HhService.VacancyDto withScore(HhService.VacancyDto vacancy, int matchScore) {
        return new HhService.VacancyDto(
                vacancy.id(), vacancy.title(), vacancy.company(), vacancy.salary(),
                vacancy.experience(), vacancy.area(), vacancy.url(), matchScore);
    }
}
