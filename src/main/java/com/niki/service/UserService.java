package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Message message) {
        Long telegramId = message.getFrom().getId();
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .telegramId(telegramId)
                            .firstName(message.getFrom().getFirstName())
                            .username(message.getFrom().getUserName())
                            .build();
                    log.info("Новый пользователь: {} (id={})", newUser.getFirstName(), telegramId);
                    return userRepository.save(newUser);
                });
    }

    @Transactional(readOnly = true)
    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + telegramId));
    }
}
