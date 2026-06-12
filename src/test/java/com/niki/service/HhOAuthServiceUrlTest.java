package com.niki.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class HhOAuthServiceUrlTest {

    @Test
    void hhAuthorizeUrlUsesEncodedParamNames() throws Exception {
        HhOAuthService service = new HhOAuthService(null, new HhOAuthStateService(), null);
        setField(service, "clientId", "TESTCLIENT");
        setField(service, "redirectUri", "https://app.onrender.com/hh/callback");

        String url = service.buildHhAuthorizeUrlWithState("abc_state");

        assertTrue(url.contains("response%5Ftype=code"), url);
        assertTrue(url.contains("client%5Fid=TESTCLIENT"), url);
        assertFalse(url.contains("client_id"), url);
        assertFalse(url.contains("response_type"), url);
    }

    @Test
    void telegramConnectUrlUsesProxyEndpoint() throws Exception {
        HhOAuthStateService stateService = new HhOAuthStateService();
        setField(stateService, "stateSecret", "test-secret");
        HhOAuthService service = new HhOAuthService(null, stateService, null);
        setField(service, "clientId", "TESTCLIENT");
        setField(service, "redirectUri", "https://app.onrender.com/hh/callback");
        setField(service, "publicBaseUrl", "https://app.onrender.com");

        String url = service.buildTelegramConnectUrl(123L);

        assertTrue(url.startsWith("https://app.onrender.com/hh/authorize?s="), url);
        assertFalse(url.contains("client_id"), url);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
