package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HhApplyServiceExtractIdTest {

    @Test
    void extractsIdFromUrl() {
        HhApplyService service = new HhApplyService(null, null, null);
        assertEquals("123456", service.extractVacancyId("https://hh.ru/vacancy/123456"));
        assertEquals("999", service.extractVacancyId("999"));
    }
}
