package com.niki.repository;

import com.niki.model.JobApplication;
import com.niki.model.JobApplication.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByUserTelegramIdAndVacancyId(Long telegramId, String vacancyId);

    List<JobApplication> findByUserTelegramIdOrderByUpdatedAtDesc(Long telegramId);

    List<JobApplication> findByUserTelegramIdAndStatusOrderByUpdatedAtDesc(
            Long telegramId, ApplicationStatus status);

    long countByUserTelegramIdAndStatus(Long telegramId, ApplicationStatus status);
}
