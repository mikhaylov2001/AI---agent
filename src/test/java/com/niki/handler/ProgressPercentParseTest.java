package com.niki.handler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ProgressPercentParseTest {

    @Test
    void parsesPlainAndPercentSuffix() throws Exception {
        Method m = CommandHandler.class.getDeclaredMethod("parseProgressPercent", String.class);
        m.setAccessible(true);
        assertEquals(37, m.invoke(null, "37"));
        assertEquals(37, m.invoke(null, "37%"));
        assertEquals(85, m.invoke(null, " 85 "));
    }

    @Test
    void rejectsOutOfRange() throws Exception {
        Method m = CommandHandler.class.getDeclaredMethod("parseProgressPercent", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, "101"));
        assertNull(m.invoke(null, "abc"));
    }
}
