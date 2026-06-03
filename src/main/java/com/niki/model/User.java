package com.niki.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "username")
    private String username;

    @Column(name = "memory_summary", columnDefinition = "TEXT")
    private String memorySummary;

    @Column(name = "mentor_profile", columnDefinition = "TEXT")
    private String mentorProfile;

    @Column(name = "hh_access_token")
    private String hhAccessToken;

    @Column(name = "hh_refresh_token")
    private String hhRefreshToken;

    @Column(name = "hh_token_expires_at")
    private LocalDateTime hhTokenExpiresAt;

    @Column(name = "hh_resume_id")
    private String hhResumeId;

    @Column(name = "proactive_enabled")
    @Builder.Default
    private Boolean proactiveEnabled = false;

    @Column(name = "job_alerts_enabled")
    @Builder.Default
    private Boolean jobAlertsEnabled = false;

    @Column(name = "job_search_query")
    @Builder.Default
    private String jobSearchQuery = "Java backend";

    @Column(name = "last_job_alert_at")
    private LocalDateTime lastJobAlertAt;

    @Column(name = "last_notified_vacancies", columnDefinition = "TEXT")
    private String lastNotifiedVacancies;

    @Column(name = "hh_search_area")
    private String hhSearchArea;

    @Column(name = "search_experience")
    private String searchExperience;

    @Column(name = "search_remote")
    @Builder.Default
    private Boolean searchRemote = false;

    @Column(name = "onboarding_done")
    @Builder.Default
    private Boolean onboardingDone = false;

    @Column(name = "session_state")
    private String sessionState;

    @Column(name = "session_payload")
    private String sessionPayload;

    @Column(name = "last_cover_letter", columnDefinition = "TEXT")
    private String lastCoverLetter;

    @Column(name = "last_cover_vacancy_id")
    private String lastCoverVacancyId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Goal> goals = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> chatHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActiveAt = LocalDateTime.now();
    }
}
