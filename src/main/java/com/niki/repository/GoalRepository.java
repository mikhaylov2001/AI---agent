package com.niki.repository;

import com.niki.model.Goal;
import com.niki.model.Goal.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserTelegramId(Long telegramId);

    List<Goal> findByUserTelegramIdAndStatus(Long telegramId, GoalStatus status);

    @Query("SELECT g FROM Goal g WHERE g.id = :id AND g.user.telegramId = :telegramId")
    Optional<Goal> findByIdAndUserTelegramId(@Param("id") Long id, @Param("telegramId") Long telegramId);

    long countByUserTelegramIdAndStatus(Long telegramId, GoalStatus status);
}
