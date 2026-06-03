package com.niki.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public enum State {
        NONE,
        AWAITING_GOAL_TITLE,
        AWAITING_JOB_QUERY,
        AWAITING_APPLY_URL,
        PROFILE_SETUP_STEP_1,
        PROFILE_SETUP_STEP_2,
        PROFILE_SETUP_STEP_3,
        PROFILE_SETUP_STEP_4
    }

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public State getState(Long telegramId) {
        return states.getOrDefault(telegramId, State.NONE);
    }

    public void setState(Long telegramId, State state) {
        if (state == State.NONE) {
            states.remove(telegramId);
        } else {
            states.put(telegramId, state);
        }
    }

    public void clear(Long telegramId) {
        states.remove(telegramId);
    }
}
