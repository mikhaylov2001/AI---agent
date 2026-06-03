package com.niki.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramHtmlTest {

    @Test
    void escapeHtmlEntities() {
        assertEquals("a &amp; b &lt; c", TelegramHtml.escape("a & b < c"));
    }

    @Test
    void markdownBoldToHtml() {
        assertEquals("Привет <b>мир</b>", TelegramHtml.markdownToHtml("Привет *мир*"));
    }
}
