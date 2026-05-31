package com.niki.handler;

import com.niki.bot.BotResponse;
import com.niki.bot.TelegramKeyboards;
import com.niki.model.Goal;
import com.niki.model.Goal.GoalCategory;
import com.niki.model.User;
import com.niki.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Locale;
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
    private final UserSessionService sessionService;

    public BotResponse handle(Message message) {
        User user = userService.getOrCreateUser(message);
        String text = message.getText().trim();
        String normalized = normalizeInput(text);

        UserSessionService.State state = sessionService.getState(user.getTelegramId());
        if (state == UserSessionService.State.AWAITING_GOAL_TITLE) {
            sessionService.clear(user.getTelegramId());
            if (normalized.startsWith("/")) {
                return handleCommand(normalized, user);
            }
            Goal goal = goalService.addGoal(user, text, GoalCategory.PERSONAL);
            return BotResponse.withMainMenu(
                    "✅ Цель добавлена: *" + goal.getTitle() + "*\n\nЧто мешало достичь её раньше?");
        }
        if (state == UserSessionService.State.AWAITING_JOB_QUERY) {
            sessionService.clear(user.getTelegramId());
            if (normalized.startsWith("/")) {
                return handleCommand(normalized, user);
            }
            return searchJobs(user, text);
        }
        if (state == UserSessionService.State.AWAITING_APPLY_URL) {
            sessionService.clear(user.getTelegramId());
            if (normalized.startsWith("/")) {
                return handleCommand(normalized, user);
            }
            return applyToVacancy(user, text);
        }

        if (normalized.startsWith("/") || isMenuButton(text)) {
            return handleCommand(normalized, user);
        }

        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        return BotResponse.withMainMenu(openAiService.chat(user, text, goals));
    }

    public BotResponse handleCallback(Long telegramId, String data) {
        User user = userService.findByTelegramId(telegramId);
        if (data.startsWith("jobs:")) {
            return searchJobs(user, data.substring(5));
        }
        return handleCommand("/" + data, user);
    }

    private BotResponse handleCommand(String command, User user) {
        String cmd = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String args = command.contains(" ") ? command.substring(command.indexOf(' ') + 1).trim() : "";

        return switch (cmd) {
            case "/start" -> startMessage(user);
            case "/help", "/помощь" -> helpMessage();
            case "/goals" -> BotResponse.withMainMenu(
                    goalService.formatGoalsForUser(goalService.getActiveGoals(user.getTelegramId())));
            case "/addgoal" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_GOAL_TITLE);
                    yield BotResponse.withMainMenu(
                            "✍️ Напиши *одним сообщением*, какую цель добавить.\n\nПример: _Сдать на права_");
                }
                Goal goal = goalService.addGoal(user, args, GoalCategory.PERSONAL);
                yield BotResponse.withMainMenu("✅ Цель добавлена: *" + goal.getTitle() + "*");
            }
            case "/jobs" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_JOB_QUERY);
                    yield BotResponse.withInline(
                            "🔍 Напиши профессию или город.\nИли нажми кнопку ниже:",
                            TelegramKeyboards.jobSearchSuggestions());
                }
                yield searchJobs(user, args);
            }
            case "/connect_hh", "/connecthh" -> connectHh(user);
            case "/hh_resumes", "/hhresumes" -> BotResponse.withMainMenu(hhApplyService.getMyResumes(user));
            case "/use_resume", "/useresume" -> {
                if (args.isBlank()) {
                    yield BotResponse.withMainMenu("Укажи ID резюме:\n/use\\_resume abc123");
                }
                yield BotResponse.withMainMenu(hhApplyService.selectResume(user, args));
            }
            case "/apply" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_APPLY_URL);
                    yield BotResponse.withMainMenu(
                            "📎 Пришли *ссылку* на вакансию с hh.ru\n\nПример: https://hh.ru/vacancy/123456");
                }
                yield applyToVacancy(user, args);
            }
            case "/confirm_apply", "/confirmapply" -> {
                if (args.isBlank()) {
                    yield BotResponse.withMainMenu("Укажи ссылку:\n/confirm\\_apply https://hh.ru/vacancy/123456");
                }
                String letter = openAiService.getLastGeneratedLetter(user);
                yield BotResponse.withMainMenu(hhApplyService.applyToVacancy(user, args, letter));
            }
            default -> BotResponse.withMainMenu(
                    "Не знаю команду. Используй кнопки внизу или /help");
        };
    }

    private BotResponse startMessage(User user) {
        return BotResponse.withMainMenu(String.format("""
                Привет, %s! 👋 Я *Ники* — твой наставник.
                
                👇 *Кнопки внизу экрана* — нажимай, не нужно печатать команды.
                📎 Меню *«/»* слева от поля ввода — список команд.
                
                Начни с *➕ Добавить цель* или *💼 Найти вакансии*.
                """, user.getFirstName()));
    }

    private BotResponse helpMessage() {
        return BotResponse.withMainMenu("""
                📖 *Помощь*
                
                *Кнопки внизу экрана:*
                🎯 Мои цели · ➕ Добавить цель
                💼 Найти вакансии · 🔗 Подключить HH
                📄 Мои резюме · ❓ Помощь
                
                *Команды:*
                /goals — цели
                /addgoal текст — добавить цель
                /jobs запрос — вакансии HH
                /connect\\_hh — вход в HH
                /hh\\_resumes — резюме
                /apply ссылка — отклик с письмом
                
                Или просто напиши мне текстом 💬
                """);
    }

    private BotResponse connectHh(User user) {
        String url = hhOAuthService.buildAuthUrl(user.getTelegramId());
        if (url.startsWith("HH_CLIENT")) {
            return BotResponse.withMainMenu(
                    "⚠️ HH не настроен на сервере.\nДобавь HH\\_CLIENT\\_ID и HH\\_CLIENT\\_SECRET в Render.");
        }
        return BotResponse.withMainMenu(
                "🔗 *Подключение HH.ru*\n\n" +
                        "1. Нажми ссылку ниже\n2. Войди в HH\n3. Вернись в Telegram\n\n" +
                        "[Авторизоваться на HH.ru](" + url + ")");
    }

    private BotResponse searchJobs(User user, String query) {
        List<HhService.VacancyDto> vacancies = hhService.searchVacancies(query, 88, 5);
        return BotResponse.withInline(
                hhService.formatVacancies(vacancies, query),
                TelegramKeyboards.jobSearchSuggestions());
    }

    private BotResponse applyToVacancy(User user, String vacancyUrl) {
        Map<String, Object> vacancy = hhApplyService.getVacancyDetails(vacancyUrl);
        if (vacancy == null) {
            return BotResponse.withMainMenu("❌ Вакансия не найдена. Проверь ссылку.");
        }
        String vacancyName = (String) vacancy.get("name");
        String raw = vacancy.get("description") != null ? vacancy.get("description").toString() : "";
        String description = raw.replaceAll("<[^>]+>", "");
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }
        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        String goalsText = goals.stream().map(Goal::getTitle).reduce((a, b) -> a + ", " + b).orElse("");
        String prompt = String.format(
                "Напиши сопроводительное письмо для вакансии '%s'.\nОписание: %s\nЦели: %s\n" +
                        "3-4 предложения, без шаблонов, на русском.",
                vacancyName, description, goalsText);
        String letter = openAiService.generateCoverLetter(user, prompt);
        return BotResponse.withMainMenu(
                "📋 *Вакансия:* " + vacancyName + "\n\n*Письмо:*\n\n" + letter +
                        "\n\nОтправить?\n/confirm\\_apply " + vacancyUrl);
    }

    private String normalizeInput(String text) {
        if (text.equals(TelegramKeyboards.BTN_GOALS)) {
            return "/goals";
        }
        if (text.equals(TelegramKeyboards.BTN_ADD_GOAL)) {
            return "/addgoal";
        }
        if (text.equals(TelegramKeyboards.BTN_JOBS)) {
            return "/jobs";
        }
        if (text.equals(TelegramKeyboards.BTN_CONNECT_HH)) {
            return "/connect_hh";
        }
        if (text.equals(TelegramKeyboards.BTN_RESUMES)) {
            return "/hh_resumes";
        }
        if (text.equals(TelegramKeyboards.BTN_HELP)) {
            return "/help";
        }

        if (!text.startsWith("/")) {
            return text;
        }

        String cmd = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String rest = text.length() > cmd.length() ? text.substring(cmd.length()) : "";
        String mapped = switch (cmd) {
            case "/connecthh" -> "/connect_hh";
            case "/hhresumes" -> "/hh_resumes";
            case "/useresume" -> "/use_resume";
            case "/confirmapply" -> "/confirm_apply";
            default -> cmd;
        };
        return mapped + rest;
    }

    private boolean isMenuButton(String text) {
        return text.equals(TelegramKeyboards.BTN_GOALS)
                || text.equals(TelegramKeyboards.BTN_ADD_GOAL)
                || text.equals(TelegramKeyboards.BTN_JOBS)
                || text.equals(TelegramKeyboards.BTN_CONNECT_HH)
                || text.equals(TelegramKeyboards.BTN_RESUMES)
                || text.equals(TelegramKeyboards.BTN_HELP);
    }
}
