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
            return "У тебя пока нет активных целей.\nНапиши /addgoal чтобы добавить первую.";
        }
        StringBuilder sb = new StringBuilder("🎯 *Твои активные цели:*\n\n");
        for (int i = 0; i < goals.size(); i++) {
            Goal g = goals.get(i);
            String emoji = getCategoryEmoji(g.getCategory());
            String bar = "█".repeat(g.getProgress() / 10) + "░".repeat(10 - g.getProgress() / 10);
            sb.append(String.format("%d. %s *%s*\n   %s %d%%\n\n",
                    i + 1, emoji, g.getTitle(), bar, g.getProgress()));
        }
        return sb.toString();
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
