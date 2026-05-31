package com.niki.repository;

import com.niki.model.Goal;
import com.niki.model.Goal.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserTelegramId(Long telegramId);

    List<Goal> findByUserTelegramIdAndStatus(Long telegramId, GoalStatus status);

    long countByUserTelegramIdAndStatus(Long telegramId, GoalStatus status);
}
