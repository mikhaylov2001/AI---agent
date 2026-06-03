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
    private final MentorProfileService mentorProfileService;

    public BotResponse handle(Message message) {
        User user = userService.getOrCreateUser(message);
        mentorProfileService.ensureDefaultProfile(user);
        String text = message.getText().trim();
        String normalized = normalizeInput(text);

        BotResponse profileStep = handleProfileSetup(user, text, normalized);
        if (profileStep != null) {
            return profileStep;
        }

        UserSessionService.State state = sessionService.getState(user.getTelegramId());
        if (state == UserSessionService.State.AWAITING_GOAL_TITLE) {
            sessionService.clear(user.getTelegramId());
            if (normalized.startsWith("/")) {
                return handleCommand(normalized, user);
            }
            Goal goal = goalService.addGoal(user, text, GoalCategory.CAREER);
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
        mentorProfileService.ensureDefaultProfile(user);

        if (data.startsWith("jobs:")) {
            return searchJobs(user, data.substring(5));
        }
        return handleCommand("/" + data, user);
    }

    private BotResponse handleProfileSetup(User user, String rawText, String normalized) {
        UserSessionService.State state = sessionService.getState(user.getTelegramId());
        int step = profileStepFromState(state);
        if (step == 0) {
            return null;
        }
        if (normalized.startsWith("/") && !normalized.equals("/cancel")) {
            sessionService.clear(user.getTelegramId());
            return handleCommand(normalized, user);
        }
        if (normalized.equals("/cancel") || rawText.equals("◀️ Главное меню")) {
            sessionService.clear(user.getTelegramId());
            return BotResponse.withMainMenu("Настройка профиля отменена.");
        }
        String next = mentorProfileService.applySetupStep(user, step, rawText);
        if (step >= 4) {
            sessionService.clear(user.getTelegramId());
            return BotResponse.withMainMenu(next);
        }
        sessionService.setState(user.getTelegramId(), profileStateForStep(step + 1));
        return BotResponse.withMainMenu(next);
    }

    private void startProfileSetup(User user) {
        sessionService.setState(user.getTelegramId(), UserSessionService.State.PROFILE_SETUP_STEP_1);
    }

    private BotResponse handleCommand(String command, User user) {
        String cmd = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String args = command.contains(" ") ? command.substring(command.indexOf(' ') + 1).trim() : "";

        return switch (cmd) {
            case "/start", "/main_menu", "/mainmenu" -> startMessage(user);
            case "/help", "/помощь" -> helpMessage();
            case "/profile", "/profil" -> BotResponse.withInlineAndMenu(
                    mentorProfileService.formatProfileForDisplay(user),
                    TelegramKeyboards.profileActions());
            case "/setup_profile", "/setupprofile" -> {
                startProfileSetup(user);
                yield BotResponse.withMainMenu(mentorProfileService.profileSetupQuestion(1));
            }
            case "/next_step", "/nextstep" -> mentorChat(user, "Дай один конкретный следующий шаг.", ChatIntent.NEXT_STEP);
            case "/checkin", "/check_in" -> mentorChat(user, "Хочу чек-ин.", ChatIntent.CHECK_IN);
            case "/learning", "/study" -> mentorChat(user, "Помоги с учёбой.", ChatIntent.LEARNING);
            case "/memory" -> mentorChat(user, "", ChatIntent.MEMORY);
            case "/goals" -> BotResponse.withMainMenu(
                    goalService.formatGoalsForUser(goalService.getActiveGoals(user.getTelegramId())));
            case "/addgoal" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_GOAL_TITLE);
                    yield BotResponse.withMainMenu(
                            "✍️ Напиши *одним сообщением*, какую цель добавить.\n\nПример: _Пройти 3 собеса_");
                }
                Goal goal = goalService.addGoal(user, args, GoalCategory.CAREER);
                yield BotResponse.withMainMenu("✅ Цель добавлена: *" + goal.getTitle() + "*");
            }
            case "/jobs" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_JOB_QUERY);
                    yield BotResponse.withInlineAndMenu(
                            "🔍 *Вакансии Java backend*\n\nНапиши запрос или нажми кнопку:",
                            TelegramKeyboards.jobSearchSuggestions());
                }
                yield searchJobs(user, args);
            }
            case "/connect_hh", "/connecthh" -> connectHh(user);
            case "/hh_resumes", "/hhresumes" -> BotResponse.withCareerMenu(hhApplyService.getMyResumes(user));
            case "/use_resume", "/useresume" -> {
                if (args.isBlank()) {
                    yield BotResponse.withCareerMenu("Укажи ID резюме:\n/use\\_resume abc123");
                }
                yield BotResponse.withCareerMenu(hhApplyService.selectResume(user, args));
            }
            case "/apply" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_APPLY_URL);
                    yield BotResponse.withCareerMenu(
                            "📎 Пришли *ссылку* на вакансию с hh.ru");
                }
                yield applyToVacancy(user, args);
            }
            case "/confirm_apply", "/confirmapply" -> {
                if (args.isBlank()) {
                    yield BotResponse.withCareerMenu("Укажи ссылку:\n/confirm\\_apply https://hh.ru/vacancy/123456");
                }
                String letter = openAiService.getLastGeneratedLetter(user);
                yield BotResponse.withCareerMenu(hhApplyService.applyToVacancy(user, args, letter));
            }
            default -> BotResponse.withMainMenu(
                    "Не знаю команду. Используй кнопки внизу или /help");
        };
    }

    private BotResponse mentorChat(User user, String text, ChatIntent intent) {
        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        return BotResponse.withMainMenu(openAiService.chat(user, text, goals, intent));
    }

    private BotResponse startMessage(User user) {
        mentorProfileService.ensureDefaultProfile(user);
        String welcome = String.format("""
                Привет, %s! 👋 Я *Ники* — твой наставник и второй мозг.
                
                *Главная цель:* Java backend разработчик.
                
                👇 *Навигация* — кнопки внизу:
                📋 След. шаг · 📊 Чек-ин · 🎯 Цели · 🧠 Профиль
                💼 Вакансии · 📚 Учёба · ❓ Помощь
                
                Или просто напиши текстом 💬
                """, user.getFirstName());

        if (!mentorProfileService.isProfileConfigured(user)) {
            welcome += "\n\n⚠️ Профиль не заполнен — нажми «📝 Настроить профиль» (4 шага, ~2 мин).";
        }

        return new BotResponse(welcome, TelegramKeyboards.mainMenu(),
                TelegramKeyboards.startInlineMenu(), true);
    }

    private BotResponse helpMessage() {
        return BotResponse.withMainMenu("""
                📖 *Навигация Ники*
                
                *Кнопки:*
                📋 След. шаг — один action на 15–60 мин
                📊 Чек-ин — состояние + фокус
                🎯 Цели · 🧠 Профиль · 📚 Учёба
                💼 Вакансии · 🔗 HH · ❓ Помощь
                
                *Команды:*
                /profile — профиль
                /setup\\_profile — настроить (4 шага)
                /goals /addgoal /jobs /apply
                
                Любой текст — диалог с памятью.
                Формат ответа: что вижу → проблема → помощь → шаг → память.
                """);
    }

    private BotResponse connectHh(User user) {
        String url = hhOAuthService.buildAuthUrl(user.getTelegramId());
        if (url.startsWith("HH_CLIENT")) {
            return BotResponse.withCareerMenu(
                    "⚠️ HH не настроен на сервере.\nДобавь HH\\_CLIENT\\_ID и HH\\_CLIENT\\_SECRET.");
        }
        return BotResponse.withCareerMenu(
                "🔗 *Подключение HH.ru*\n\n" +
                        "1. Нажми ссылку\n2. Войди в HH\n3. Вернись в Telegram\n\n" +
                        "[Авторизоваться](" + url + ")");
    }

    private BotResponse searchJobs(User user, String query) {
        List<HhService.VacancyDto> vacancies = hhService.searchVacancies(query, 88, 5);
        return BotResponse.withInlineAndMenu(
                hhService.formatVacancies(vacancies, query),
                TelegramKeyboards.jobSearchSuggestions());
    }

    private BotResponse applyToVacancy(User user, String vacancyUrl) {
        Map<String, Object> vacancy = hhApplyService.getVacancyDetails(vacancyUrl);
        if (vacancy == null) {
            return BotResponse.withCareerMenu("❌ Вакансия не найдена. Проверь ссылку.");
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
        return BotResponse.withCareerMenu(
                "📋 *Вакансия:* " + vacancyName + "\n\n*Письмо:*\n\n" + letter +
                        "\n\nОтправить?\n/confirm\\_apply " + vacancyUrl);
    }

    private String normalizeInput(String text) {
        if (text.equals(TelegramKeyboards.BTN_GOALS)) return "/goals";
        if (text.equals(TelegramKeyboards.BTN_JOBS)) return "/jobs";
        if (text.equals(TelegramKeyboards.BTN_CONNECT_HH)) return "/connect_hh";
        if (text.equals(TelegramKeyboards.BTN_RESUMES)) return "/hh_resumes";
        if (text.equals(TelegramKeyboards.BTN_HELP)) return "/help";
        if (text.equals(TelegramKeyboards.BTN_PROFILE)) return "/profile";
        if (text.equals(TelegramKeyboards.BTN_NEXT_STEP)) return "/next_step";
        if (text.equals(TelegramKeyboards.BTN_CHECKIN)) return "/checkin";
        if (text.equals(TelegramKeyboards.BTN_LEARNING)) return "/learning";
        if (text.equals(TelegramKeyboards.BTN_SETUP_PROFILE)) return "/setup_profile";
        if (text.equals("◀️ Главное меню")) return "/main_menu";

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
            case "/setupprofile" -> "/setup_profile";
            case "/nextstep" -> "/next_step";
            case "/mainmenu" -> "/main_menu";
            default -> cmd;
        };
        return mapped + rest;
    }

    private boolean isMenuButton(String text) {
        return text.equals(TelegramKeyboards.BTN_GOALS)
                || text.equals(TelegramKeyboards.BTN_JOBS)
                || text.equals(TelegramKeyboards.BTN_CONNECT_HH)
                || text.equals(TelegramKeyboards.BTN_RESUMES)
                || text.equals(TelegramKeyboards.BTN_HELP)
                || text.equals(TelegramKeyboards.BTN_PROFILE)
                || text.equals(TelegramKeyboards.BTN_NEXT_STEP)
                || text.equals(TelegramKeyboards.BTN_CHECKIN)
                || text.equals(TelegramKeyboards.BTN_LEARNING)
                || text.equals(TelegramKeyboards.BTN_SETUP_PROFILE)
                || text.equals("◀️ Главное меню");
    }

    private static int profileStepFromState(UserSessionService.State state) {
        return switch (state) {
            case PROFILE_SETUP_STEP_1 -> 1;
            case PROFILE_SETUP_STEP_2 -> 2;
            case PROFILE_SETUP_STEP_3 -> 3;
            case PROFILE_SETUP_STEP_4 -> 4;
            default -> 0;
        };
    }

    private static UserSessionService.State profileStateForStep(int step) {
        return switch (step) {
            case 1 -> UserSessionService.State.PROFILE_SETUP_STEP_1;
            case 2 -> UserSessionService.State.PROFILE_SETUP_STEP_2;
            case 3 -> UserSessionService.State.PROFILE_SETUP_STEP_3;
            case 4 -> UserSessionService.State.PROFILE_SETUP_STEP_4;
            default -> UserSessionService.State.NONE;
        };
    }
}
