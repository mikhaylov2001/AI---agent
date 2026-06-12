package com.niki.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoalServiceTest {

    @Test
    void progressLineUsesFiveSegments() {
        assertEquals("⬜⬜⬜⬜⬜  *0%*", GoalService.progressLine(0));
        assertEquals("🟩🟩⬜⬜⬜  *50%*", GoalService.progressLine(50));
        assertEquals("🟩🟩🟩🟩🟩  *100%*", GoalService.progressLine(100));
    }
}
