package com.niki.service;

import com.niki.model.Goal;
import com.niki.model.Goal.*;
import com.niki.model.User;
import com.niki.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    @Transactional
    public Goal addGoal(User user, String title, GoalCategory category) {
        Goal goal = Goal.builder()
                .user(user)
                .title(title)
                .category(category)
                .priority(GoalPriority.MEDIUM)
                .status(GoalStatus.ACTIVE)
                .progress(0)
                .build();
        return goalRepository.save(goal);
    }

    @Transactional(readOnly = true)
    public List<Goal> getActiveGoals(Long telegramId) {
        return goalRepository.findByUserTelegramIdAndStatus(telegramId, GoalStatus.ACTIVE);
    }

    @Transactional
    public Goal updateProgressByIndex(Long telegramId, int oneBasedIndex, int progress) {
        List<Goal> goals = getActiveGoals(telegramId);
        if (oneBasedIndex < 1 || oneBasedIndex > goals.size()) {
            throw new IllegalArgumentException("Цель #" + oneBasedIndex + " не найдена");
        }
        return updateProgress(goals.get(oneBasedIndex - 1).getId(), progress);
    }

    @Transactional
    public Goal updateProgressForUser(Long telegramId, Long goalId, int progress) {
        Goal goal = goalRepository.findByIdAndUserTelegramId(goalId, telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        return updateProgress(goal.getId(), progress);
    }

    @Transactional
    public Goal updateProgress(Long goalId, int progress) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Цель не найдена: " + goalId));
        goal.setProgress(Math.min(100, Math.max(0, progress)));
        if (goal.getProgress() == 100) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(LocalDateTime.now());
        }
        return goalRepository.save(goal);
    }

    public String formatGoalsForUser(List<Goal> goals) {
        if (goals.isEmpty()) {
            return """
                    🎯 *Мои цели*
                    
                    Пока пусто — добавь первую цель кнопкой ниже 👇""";
        }
        StringBuilder sb = new StringBuilder("🎯 *Мои цели*\n\n");
        for (int i = 0; i < goals.size(); i++) {
            Goal g = goals.get(i);
            String emoji = getCategoryEmoji(g.getCategory());
            sb.append("*").append(i + 1).append(".* ").append(emoji).append(" ").append(g.getTitle()).append("\n");
            sb.append(progressLine(g.getProgress())).append("\n\n");
        }
        sb.append("_Обновить прогресс — кнопки под сообщением_");
        return sb.toString().trim();
    }

    public static String progressLine(int percent) {
        int p = Math.min(100, Math.max(0, percent));
        int filled = Math.min(5, p * 5 / 100);
        return "🟩".repeat(filled) + "⬜".repeat(5 - filled) + "  *" + p + "%*";
    }

    private String getCategoryEmoji(GoalCategory category) {
        return switch (category) {
            case CAREER -> "💼";
            case FITNESS -> "💪";
            case LEARNING -> "📚";
            case FINANCE -> "💰";
            case PERSONAL -> "🌟";
            case PROJECT -> "🚀";
            default -> "📌";
        };
    }
}
