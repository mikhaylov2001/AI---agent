package com.niki.service;

/** HH OAuth: нет токена, refresh не удался или сессия отозвана. */
public class HhAuthException extends RuntimeException {

    public HhAuthException(String message) {
        super(message);
    }

    public HhAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
