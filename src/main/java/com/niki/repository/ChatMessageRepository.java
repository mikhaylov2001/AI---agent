package com.niki.repository;

import com.niki.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.user.telegramId = :telegramId
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findByUserTelegramIdOrderByCreatedAtAsc(@Param("telegramId") Long telegramId, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM ChatMessage m
            WHERE m.user.telegramId = :telegramId AND m.id < :maxId
            """)
    void deleteByUserTelegramIdAndIdLessThan(@Param("telegramId") Long telegramId, @Param("maxId") Long maxId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.user.telegramId = :telegramId")
    void deleteAllByUserTelegramId(@Param("telegramId") Long telegramId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.user.telegramId = :telegramId")
    long countByUserTelegramId(@Param("telegramId") Long telegramId);
}
