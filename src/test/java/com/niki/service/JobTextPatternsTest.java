package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobTextPatternsTest {

    @Test
    void resumeListRequestIsNotJobSearch() {
        String lower = JobTextPatterns.normalize("Резюме");
        assertTrue(JobTextPatterns.isResumeListRequest(lower));
        assertFalse(JobTextPatterns.isJobRelated(lower));
    }

    @Test
    void detectsJavaDeveloperTypo() {
        assertTrue(JobTextPatterns.isJobRelated("java develover"));
    }

    @Test
    void detectsSelectBestVacancies() {
        String lower = JobTextPatterns.normalize("выбери сам какие лучше и присылай сообщениями");
        assertTrue(JobTextPatterns.isJobThreadContinuation(lower));
    }

    @Test
    void extractsQueryFromTypo() {
        assertEquals("Java developer", JobTextPatterns.extractSearchQuery("java develover", null));
    }

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
