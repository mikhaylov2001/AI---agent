package com.niki.handler;

import com.niki.model.Goal;
import com.niki.model.Goal.GoalCategory;
import com.niki.model.User;
import com.niki.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommandHandler {

    private final UserService userService;
    private final GoalService goalService;
    private final OpenAiService openAiService;
    private final HhService hhService;
    private final HhOAuthService hhOAuthService;
    private final HhApplyService hhApplyService;

    public String handle(Message message) {
        User user = userService.getOrCreateUser(message);
        String text = message.getText().trim();
        if (text.startsWith("/")) {
            return handleCommand(text, user);
        }
        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        return openAiService.chat(user, text, goals);
    }

    private String handleCommand(String command, User user) {
        if (command.equals("/start")) {
            return String.format("""
                    Привет, %s! 👋 Я Ники — твой личный наставник.
                    
                    Я буду:
                    🎯 Следить за твоими целями
                    💡 Советовать что делать
                    ⏰ Писать тебе утром и вечером
                    🧠 Помнить всё что ты говорил
                    💼 Искать вакансии и откликаться за тебя
                    
                    *Команды:*
                    /goals — мои цели
                    /addgoal [цель] — добавить цель
                    /jobs [запрос] — найти вакансии на HH
                    /connect_hh — подключить HH аккаунт
                    /hh_resumes — мои резюме
                    /apply [ссылка] — откликнуться с письмом
                    /help — все команды
                    """, user.getFirstName());
        }
        if (command.equals("/goals")) {
            return goalService.formatGoalsForUser(goalService.getActiveGoals(user.getTelegramId()));
        }
        if (command.startsWith("/addgoal")) {
            String[] parts = command.split(" ", 2);
            if (parts.length < 2 || parts[1].isBlank()) {
                return "Напиши цель после команды.\nПример: /addgoal Сдать экзамен по вождению";
            }
            Goal goal = goalService.addGoal(user, parts[1].trim(), GoalCategory.PERSONAL);
            return "✅ Цель добавлена: *" + goal.getTitle() + "*\n\nЧто мешало достичь её раньше?";
        }
        if (command.startsWith("/jobs")) {
            String[] parts = command.split(" ", 2);
            String query = parts.length > 1 ? parts[1].trim() : "Java developer";
            List<HhService.VacancyDto> vacancies = hhService.searchVacancies(query, 88, 5);
            return hhService.formatVacancies(vacancies, query);
        }
        if (command.equals("/connect_hh")) {
            String url = hhOAuthService.buildAuthUrl(user.getTelegramId());
            return "🔗 *Подключение HH.ru*\n\n" +
                    "1. Перейди по ссылке\n2. Войди в HH\n3. Вернись в Telegram\n\n" +
                    "[Авторизоваться на HH.ru](" + url + ")";
        }
        if (command.equals("/hh_resumes")) {
            return hhApplyService.getMyResumes(user);
        }
        if (command.startsWith("/use_resume")) {
            String[] parts = command.split(" ", 2);
            if (parts.length < 2) {
                return "Укажи ID: /use_resume abc123";
            }
            return hhApplyService.selectResume(user, parts[1].trim());
        }
        if (command.startsWith("/apply")) {
            String[] parts = command.split(" ", 2);
            if (parts.length < 2) {
                return "Укажи ссылку:\n/apply https://hh.ru/vacancy/123456";
            }
            String vacancyUrl = parts[1].trim();
            Map<String, Object> vacancy = hhApplyService.getVacancyDetails(vacancyUrl);
            if (vacancy == null) {
                return "❌ Вакансия не найдена. Проверь ссылку.";
            }
            String vacancyName = (String) vacancy.get("name");
            String description = vacancy.get("description") != null
                    ? vacancy.get("description").toString().replaceAll("<[^>]+>", "").substring(0, Math.min(500, vacancy.get("description").toString().length()))
                    : "";
            List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
            String goalsText = goals.stream().map(Goal::getTitle).reduce((a, b) -> a + ", " + b).orElse("");
            String prompt = String.format(
                    "Напиши сопроводительное письмо для вакансии '%s'.\n" +
                            "Описание вакансии: %s\n" +
                            "Мои цели и навыки: %s\n" +
                            "Письмо: 3-4 предложения, конкретно, без шаблонных фраз. На русском.",
                    vacancyName, description, goalsText);
            String letter = openAiService.generateCoverLetter(user, prompt);
            return "📋 *Вакансия:* " + vacancyName + "\n\n" +
                    "*Сопроводительное письмо:*\n\n" + letter + "\n\n" +
                    "Отправить этот отклик?\n/confirm\\_apply " + vacancyUrl;
        }
        if (command.startsWith("/confirm_apply")) {
            String[] parts = command.split(" ", 2);
            if (parts.length < 2) {
                return "Укажи ссылку: /confirm_apply https://hh.ru/vacancy/123456";
            }
            String letter = openAiService.getLastGeneratedLetter(user);
            return hhApplyService.applyToVacancy(user, parts[1].trim(), letter);
        }
        if (command.equals("/help")) {
            return """
                    📖 *Все команды Ники:*
                    
                    *Цели:*
                    /goals — мои активные цели
                    /addgoal [цель] — добавить цель
                    
                    *Работа (HH.ru):*
                    /jobs [запрос] — найти вакансии
                    /connect_hh — подключить HH аккаунт
                    /hh_resumes — список моих резюме
                    /use_resume [ID] — выбрать резюме
                    /apply [ссылка] — откликнуться с письмом
                    /confirm_apply [ссылка] — подтвердить отклик
                    
                    *Общее:*
                    /start — начать заново
                    /help — эта подсказка
                    
                    Или просто пиши мне как другу 🙂
                    """;
        }
        return "Не знаю такой команды. Напиши /help";
    }
}
