package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobTextPatternsTest {

    @Test
    void detectsOpenYourselfRequest() {
        assertTrue(JobTextPatterns.wantsBotToSearch("открой сам"));
    }

    @Test
    void detectsJuniorMiddleVacancyRequest() {
        String lower = JobTextPatterns.normalize("надо только вакансии джуниора и мидла");
        assertTrue(JobTextPatterns.isJobRelated(lower));
        assertTrue(JobTextPatterns.isJobSearchRequest(lower));
    }
}
