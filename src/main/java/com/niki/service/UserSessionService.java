package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
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

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public State getState(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .map(User::getSessionState)
                .filter(StringUtils::hasText)
                .map(this::parseState)
                .orElse(State.NONE);
    }

    @Transactional
    public void setState(Long telegramId, State state) {
        userRepository.findByTelegramId(telegramId).ifPresent(user -> {
            user.setSessionState(state == State.NONE ? null : state.name());
            userRepository.save(user);
        });
    }

    @Transactional
    public void clear(Long telegramId) {
        setState(telegramId, State.NONE);
    }

    @Transactional
    public void setPayload(Long telegramId, String payload) {
        userRepository.findByTelegramId(telegramId).ifPresent(user -> {
            user.setSessionPayload(payload);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public String getPayload(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .map(User::getSessionPayload)
                .orElse(null);
    }

    private State parseState(String raw) {
        try {
            return State.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return State.NONE;
        }
    }
}
