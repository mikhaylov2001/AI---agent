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

    @Test
    void preservesUnderscoresInOAuthUrls() {
        String url = "https://hh.ru/oauth/authorize?response_type=code&client_id=ABC&redirect_uri=https%3A%2F%2Fapp.com%2Fcb";
        String md = "[Авторизоваться](" + url + ")";
        String html = TelegramHtml.markdownToHtml(md);
        assertTrue(html.contains("client_id=ABC"), "html was: " + html);
        assertTrue(html.contains("response_type=code"), "html was: " + html);
        assertFalse(html.contains("clientid"), "html was: " + html);
    }
}
