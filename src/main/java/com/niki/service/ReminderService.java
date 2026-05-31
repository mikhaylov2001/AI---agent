package com.niki.service;

import com.niki.model.Goal;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final UserRepository userRepository;
    private final GoalService goalService;
    private NikiMessageSender messageSender;

    public void setMessageSender(NikiMessageSender sender) {
        this.messageSender = sender;
    }

    @Scheduled(cron = "${niki.morning-cron}")
    public void sendMorningReminder() {
        log.info("Отправка утренних напоминаний...");
        for (User user : userRepository.findAll()) {
            List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
            if (goals.isEmpty() || messageSender == null) {
                continue;
            }
            Goal top = goals.stream()
                    .filter(g -> g.getPriority() == Goal.GoalPriority.HIGH)
                    .findFirst().orElse(goals.get(0));
            messageSender.sendMessage(user.getTelegramId(), String.format(
                    "☀️ Доброе утро, %s!\n\nСегодня у тебя %d активных целей.\n" +
                            "Главная: *%s* (%d%%)\n\nЧто сделаешь сегодня для неё? 💪",
                    user.getFirstName(), goals.size(), top.getTitle(), top.getProgress()));
        }
    }

    @Scheduled(cron = "${niki.evening-cron}")
    public void sendEveningReminder() {
        log.info("Отправка вечерних напоминаний...");
        for (User user : userRepository.findAll()) {
            List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
            if (goals.isEmpty() || messageSender == null) {
                continue;
            }
            messageSender.sendMessage(user.getTelegramId(), String.format(
                    "🌙 Вечер, %s! Время подвести итог.\n\nЧто сделал сегодня для своих целей?\n" +
                            "Расскажи — я запомню и учту завтра 🙂",
                    user.getFirstName()));
        }
    }
}
