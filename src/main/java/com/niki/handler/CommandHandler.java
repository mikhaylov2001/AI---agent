package com.niki.handler;

import com.niki.bot.BotResponse;
import com.niki.bot.TelegramKeyboards;
import com.niki.model.Goal;
import com.niki.model.Goal.GoalCategory;
import com.niki.model.JobApplication.ApplicationStatus;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import com.niki.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommandHandler {

    private final UserService userService;
    private final UserRepository userRepository;
    private final GoalService goalService;
    private final LlmService llmService;
    private final HhService hhService;
    private final HhOAuthService hhOAuthService;
    private final HhApplyService hhApplyService;
    private final UserSessionService sessionService;
    private final MentorProfileService mentorProfileService;
    private final ProactiveAgentService proactiveAgentService;
    private final JobApplicationService jobApplicationService;

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
            return goalsResponse(user, "✅ Цель добавлена: *" + goal.getTitle() + "*");
        }
        if (state == UserSessionService.State.AWAITING_GOAL_PROGRESS) {
            String goalIdPayload = sessionService.getPayload(user.getTelegramId());
            sessionService.clear(user.getTelegramId());
            if (normalized.startsWith("/")) {
                return handleCommand(normalized, user);
            }
            Integer percent = parseProgressPercent(text);
            if (percent == null) {
                return goalsResponse(user, "❌ Нужно число от 0 до 100.\n_Пример:_ 37 или 37%");
            }
            return applyGoalProgress(user, goalIdPayload + ":" + percent);
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
        return BotResponse.withMainMenu(llmService.chat(user, text, goals));
    }

    public BotResponse handleCallback(Long telegramId, String data) {
        User user = userService.findByTelegramId(telegramId);
        mentorProfileService.ensureDefaultProfile(user);

        if (data.startsWith("jobs:")) {
            return searchJobs(user, data.substring(5));
        }
        if (data.startsWith("filter:")) {
            return handleFilterCallback(user, data.substring(7));
        }
        if (data.startsWith("apply:")) {
            return startApplyById(user, data.substring(6));
        }
        if (data.startsWith("save:")) {
            return saveVacancyById(user, data.substring(5));
        }
        if (data.startsWith("skip:")) {
            return skipVacancyById(user, data.substring(5));
        }
        if (data.startsWith("confirm:")) {
            return confirmApplyById(user, data.substring(8));
        }
        if (data.startsWith("letter:")) {
            return handleLetterCallback(user, data.substring(7));
        }
        if (data.startsWith("resume:")) {
            return BotResponse.withCareerMenu(hhApplyService.selectResume(user, data.substring(7)));
        }
        if (data.startsWith("autopilot:")) {
            return handleAutopilotCallback(user, data.substring(10));
        }
        if ("goals".equals(data)) {
            return goalsResponse(user, null);
        }
        if ("addgoal".equals(data)) {
            sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_GOAL_TITLE);
            return BotResponse.withMainMenu(
                    "✍️ Напиши *одним сообщением*, какую цель добавить.\n\n_Пример:_ Пройти 3 собеса");
        }
        if (data.startsWith("goalpick:")) {
            return goalPickProgress(user, Long.parseLong(data.substring(9)));
        }
        if (data.startsWith("progress:")) {
            return applyGoalProgress(user, data.substring(9));
        }
        if (data.startsWith("progresscustom:")) {
            return startCustomProgressInput(user, Long.parseLong(data.substring(15)));
        }
        return handleCommand("/" + data, user);
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
            case "/interview" -> mentorChat(user, StringUtils.hasText(args) ? args : "Подготовь меня к собеседованию Java backend.", ChatIntent.INTERVIEW);
            case "/memory" -> mentorChat(user, "", ChatIntent.MEMORY);
            case "/цели", "/goals" -> goalsResponse(user, null);
            case "/сброс", "/сбросить", "/reset" -> {
                llmService.clearConversationMemory(user);
                yield BotResponse.withMainMenu(
                        "🗑 История и память сброшены.\n\nНачнём с чистого листа — напиши, с чего начнём.");
            }
            case "/progress" -> handleProgress(user, args);
            case "/addgoal" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_GOAL_TITLE);
                    yield BotResponse.withMainMenu(
                            "✍️ Напиши *одним сообщением*, какую цель добавить.\n\nПример: _Пройти 3 собеса_");
                }
                Goal goal = goalService.addGoal(user, args, GoalCategory.CAREER);
                yield goalsResponse(user, "✅ Цель добавлена: *" + goal.getTitle() + "*");
            }
            case "/jobs" -> {
                if (args.isBlank()) {
                    yield searchJobs(user, "Java backend developer");
                }
                yield searchJobs(user, args);
            }
            case "/applications", "/my_apps", "/myapps" -> BotResponse.withCareerMenu(jobApplicationService.formatApplications(user));
            case "/connect_hh", "/connecthh" -> connectHh(user);
            case "/hh_resumes", "/hhresumes" -> hhResumesResponse(user);
            case "/use_resume", "/useresume" -> {
                if (args.isBlank()) {
                    yield BotResponse.withCareerMenu("Укажи ID резюме:\n/use\\_resume abc123");
                }
                yield BotResponse.withCareerMenu(hhApplyService.selectResume(user, args));
            }
            case "/apply" -> {
                if (args.isBlank()) {
                    sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_APPLY_URL);
                    yield BotResponse.withCareerMenu("📎 Пришли *ссылку* или ID вакансии с hh.ru");
                }
                yield applyToVacancy(user, args);
            }
            case "/confirm_apply", "/confirmapply" -> {
                if (args.isBlank()) {
                    yield BotResponse.withCareerMenu("Укажи ссылку или ID:\n/confirm\\_apply 123456");
                }
                yield confirmApplyById(user, hhApplyService.extractVacancyId(args));
            }
            case "/autopilot" -> {
                if (args.isBlank()) {
                    yield BotResponse.withMainMenu(proactiveAgentService.autopilotStatus(user));
                }
                yield BotResponse.withMainMenu(proactiveAgentService.setAutopilot(user, parseOnOff(args)));
            }
            case "/job_alerts", "/jobalerts" -> {
                if (args.isBlank()) {
                    yield BotResponse.withMainMenu(proactiveAgentService.autopilotStatus(user));
                }
                yield BotResponse.withMainMenu(proactiveAgentService.setJobAlerts(user, parseOnOff(args)));
            }
            case "/job_query", "/jobquery" -> {
                if (args.isBlank()) {
                    yield BotResponse.withMainMenu("Укажи запрос:\n/job\\_query Java backend Spring");
                }
                yield BotResponse.withMainMenu(proactiveAgentService.setJobQuery(user, args));
            }
            case "/area" -> {
                user.setHhSearchArea(args.isBlank() ? null : args.trim());
                userRepository.save(user);
                yield BotResponse.withMainMenu("✅ Регион поиска: " + (args.isBlank() ? "вся Россия" : args));
            }
            default -> BotResponse.withMainMenu(
                    "Не знаю команду. Используй кнопки внизу или /help");
        };
    }

    private BotResponse handleProgress(User user, String args) {
        if (args.isBlank()) {
            return goalsResponse(user, null);
        }
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return goalsResponse(user, "Формат: /progress 1 40 — или кнопки под 🎯 Цели");
        }
        try {
            int index = Integer.parseInt(parts[0]);
            int progress = Integer.parseInt(parts[1]);
            Goal goal = goalService.updateProgressByIndex(user.getTelegramId(), index, progress);
            return goalsResponse(user, "✅ *" + goal.getTitle() + "* → " + goal.getProgress() + "%");
        } catch (Exception e) {
            return BotResponse.withMainMenu("❌ " + e.getMessage());
        }
    }

    private BotResponse goalsResponse(User user, String prefix) {
        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        String body = goalService.formatGoalsForUser(goals);
        if (StringUtils.hasText(prefix)) {
            body = prefix + "\n\n" + body;
        }
        if (goals.isEmpty()) {
            body = body + "\n\n" + mentorProfileService.formatCoreGoals();
            return BotResponse.withInlineAndMenu(body, TelegramKeyboards.goalsEmptyActions());
        }
        return BotResponse.withInlineAndMenu(body, TelegramKeyboards.goalProgressPicker(goals));
    }

    private BotResponse goalPickProgress(User user, Long goalId) {
        Goal goal = goalService.getActiveGoals(user.getTelegramId()).stream()
                .filter(g -> g.getId().equals(goalId))
                .findFirst()
                .orElse(null);
        if (goal == null) {
            return goalsResponse(user, "❌ Цель не найдена");
        }
        String text = "📈 *" + goal.getTitle() + "*\n\nСейчас: " + GoalService.progressLine(goal.getProgress())
                + "\n\nВыбери прогресс или *✏️ Свой %*:";
        return BotResponse.withInlineAndMenu(text, TelegramKeyboards.goalSetProgress(goalId));
    }

    private BotResponse startCustomProgressInput(User user, Long goalId) {
        Goal goal = goalService.getActiveGoals(user.getTelegramId()).stream()
                .filter(g -> g.getId().equals(goalId))
                .findFirst()
                .orElse(null);
        if (goal == null) {
            return goalsResponse(user, "❌ Цель не найдена");
        }
        sessionService.setState(user.getTelegramId(), UserSessionService.State.AWAITING_GOAL_PROGRESS);
        sessionService.setPayload(user.getTelegramId(), String.valueOf(goalId));
        return BotResponse.withMainMenu(
                "✏️ *" + goal.getTitle() + "*\n\nНапиши прогресс числом *0–100*.\n_Примеры:_ 37 · 37% · 85");
    }

    private static Integer parseProgressPercent(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String digits = text.trim().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(digits);
            if (value < 0 || value > 100) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BotResponse applyGoalProgress(User user, String payload) {
        String[] parts = payload.split(":");
        if (parts.length < 2) {
            return goalsResponse(user, "❌ Ошибка кнопки");
        }
        try {
            long goalId = Long.parseLong(parts[0]);
            int progress = Integer.parseInt(parts[1]);
            Goal goal = goalService.updateProgressForUser(user.getTelegramId(), goalId, progress);
            String note = goal.getProgress() == 100
                    ? "🎉 *" + goal.getTitle() + "* — готово!"
                    : "✅ *" + goal.getTitle() + "* → " + goal.getProgress() + "%";
            return goalsResponse(user, note);
        } catch (Exception e) {
            return goalsResponse(user, "❌ " + e.getMessage());
        }
    }

    private BotResponse hhResumesResponse(User user) {
        String text = hhApplyService.getMyResumes(user);
        List<Map<String, String>> resumes = hhApplyService.listResumes(user);
        if (resumes.isEmpty()) {
            return BotResponse.withCareerMenu(text);
        }
        return BotResponse.withInlineAndMenu(text, TelegramKeyboards.resumePicker(resumes));
    }

    private BotResponse handleFilterCallback(User user, String data) {
        String[] parts = data.split(":", 3);
        String type = parts[0];
        String query = user.getJobSearchQuery();
        if ("remote".equals(type) && parts.length > 1) {
            query = parts[1];
        }
        if ("exp".equals(type) && parts.length > 2) {
            query = parts[2];
        }
        if ("salary".equals(type) && parts.length > 1) {
            query = parts[1];
        }

        HhSearchFilters base = HhSearchFilters.fromUser(user, query, "", hhService.getSearchPerPage());
        HhSearchFilters filters = switch (type) {
            case "remote" -> {
                user.setSearchRemote(true);
                userRepository.save(user);
                yield new HhSearchFilters(query, base.area(), base.experience(), true, base.onlyWithSalary(), base.perPage());
            }
            case "exp" -> {
                String exp = parts.length > 1 ? parts[1] : "between1And3";
                user.setSearchExperience(exp);
                userRepository.save(user);
                yield base.withExperience(exp);
            }
            case "salary" -> new HhSearchFilters(query, base.area(), base.experience(), base.remote(), true, base.perPage());
            default -> base;
        };
        return buildSearchResponse(hhService.searchVacancies(filters));
    }

    private BotResponse handleAutopilotCallback(User user, String mode) {
        user.setOnboardingDone(true);
        return switch (mode) {
            case "on:both" -> {
                proactiveAgentService.setAutopilot(user, true);
                proactiveAgentService.setJobAlerts(user, true);
                yield BotResponse.withMainMenu("✅ Автопилот и алерты включены!\n\n/job\\_query — настроить запрос вакансий");
            }
            case "on:alerts" -> BotResponse.withMainMenu(proactiveAgentService.setJobAlerts(user, true));
            case "off" -> {
                proactiveAgentService.setAutopilot(user, false);
                proactiveAgentService.setJobAlerts(user, false);
                yield BotResponse.withMainMenu("⏸ Автопилот выключен. Включить: /autopilot on");
            }
            default -> BotResponse.withMainMenu(proactiveAgentService.autopilotStatus(user));
        };
    }

    @Transactional
    protected BotResponse startApplyById(User user, String vacancyId) {
        Map<String, Object> vacancy = hhApplyService.getVacancyDetails(vacancyId);
        if (vacancy == null) {
            return BotResponse.withCareerMenu("❌ Вакансия не найдена.");
        }
        return buildApplyPreview(user, vacancy, vacancyId);
    }

    private BotResponse applyToVacancy(User user, String vacancyUrl) {
        String vacancyId = hhApplyService.extractVacancyId(vacancyUrl);
        Map<String, Object> vacancy = hhApplyService.getVacancyDetails(vacancyId);
        if (vacancy == null) {
            return BotResponse.withCareerMenu("❌ Вакансия не найдена. Проверь ссылку.");
        }
        return buildApplyPreview(user, vacancy, vacancyId);
    }

    private BotResponse buildApplyPreview(User user, Map<String, Object> vacancy, String vacancyId) {
        String vacancyName = (String) vacancy.get("name");
        String description = hhApplyService.plainDescription(vacancy);
        int matchScore = llmService.scoreVacancyMatch(user, vacancyName, description);
        String resumeSummary = hhApplyService.getResumeSummary(user);
        String letter = llmService.generateCoverLetter(user, vacancyName, description, resumeSummary, matchScore);

        HhService.VacancyDto dto = new HhService.VacancyDto(
                vacancyId, vacancyName, extractCompany(vacancy), "—", "", "", buildVacancyUrl(vacancyId), matchScore);
        jobApplicationService.upsert(user, dto, ApplicationStatus.LETTER_DRAFTED);
        jobApplicationService.saveLetter(user, vacancyId, letter, matchScore);

        user.setLastCoverLetter(letter);
        user.setLastCoverVacancyId(vacancyId);
        userRepository.save(user);

        String msg = String.format("""
                📋 *%s*
                🎯 Match: *%d%%*
                
                *Письмо:*
                %s
                """, vacancyName, matchScore, letter);

        if (matchScore < 40) {
            msg += "\n\n⚠️ _Низкий match — возможно, стоит пропустить или доработать резюме._";
        }

        return new BotResponse(msg, TelegramKeyboards.careerMenu(),
                TelegramKeyboards.coverLetterActions(vacancyId), true);
    }

    @Transactional
    protected BotResponse confirmApplyById(User user, String vacancyId) {
        String letter = StringUtils.hasText(user.getLastCoverLetter())
                ? user.getLastCoverLetter()
                : llmService.getLastGeneratedLetter(user);
        String result = hhApplyService.applyToVacancy(user, vacancyId, letter);
        if (result.startsWith("✅")) {
            jobApplicationService.markApplied(user, vacancyId);
        }
        return BotResponse.withCareerMenu(result);
    }

    private BotResponse handleLetterCallback(User user, String data) {
        String[] parts = data.split(":", 2);
        if (parts.length < 2) {
            return BotResponse.withCareerMenu("❌ Неверная команда");
        }
        String action = parts[0];
        String vacancyId = parts[1];
        String letter = StringUtils.hasText(user.getLastCoverLetter())
                ? user.getLastCoverLetter()
                : llmService.getLastGeneratedLetter(user);
        String instruction = switch (action) {
            case "short" -> "Сделай короче — максимум 2 предложения";
            case "spring" -> "Добавь упоминание опыта со Spring Boot и pet-проектов";
            default -> "Улучши письмо";
        };
        String updated = llmService.rewriteCoverLetter(user, letter, instruction);
        user.setLastCoverLetter(updated);
        user.setLastCoverVacancyId(vacancyId);
        userRepository.save(user);
        jobApplicationService.saveLetter(user, vacancyId, updated, null);
        return new BotResponse(
                "📝 *Обновлённое письмо:*\n\n" + updated,
                TelegramKeyboards.careerMenu(),
                TelegramKeyboards.coverLetterActions(vacancyId),
                true);
    }

    private BotResponse saveVacancyById(User user, String vacancyId) {
        Map<String, Object> v = hhApplyService.getVacancyDetails(vacancyId);
        if (v == null) {
            return BotResponse.withCareerMenu("❌ Вакансия не найдена");
        }
        HhService.VacancyDto dto = mapVacancy(v, vacancyId);
        jobApplicationService.upsert(user, dto, ApplicationStatus.SAVED);
        return BotResponse.withCareerMenu("💾 Сохранено: *" + dto.title() + "*\n/applications — все отклики");
    }

    private BotResponse skipVacancyById(User user, String vacancyId) {
        Map<String, Object> v = hhApplyService.getVacancyDetails(vacancyId);
        if (v != null) {
            jobApplicationService.upsert(user, mapVacancy(v, vacancyId), ApplicationStatus.SKIPPED);
        }
        return BotResponse.withMainMenu("⏭ Пропущено. Ищем дальше — 💼 Вакансии");
    }

    private BotResponse mentorChat(User user, String text, ChatIntent intent) {
        List<Goal> goals = goalService.getActiveGoals(user.getTelegramId());
        return BotResponse.withMainMenu(llmService.chat(user, text, goals, intent));
    }

    private BotResponse startMessage(User user) {
        mentorProfileService.ensureDefaultProfile(user);
        String welcome = String.format("""
                Привет, %s! 👋 Я *Ники* — твой наставник и второй мозг.
                
                *Главная цель:* Java backend разработчик.
                
                👇 *Навигация* — кнопки внизу:
                📋 След. шаг · 📊 Чек-ин · 🎯 Цели · 🧠 Профиль
                💼 Вакансии · 📋 Отклики · 🎤 Собес · 📚 Учёба
                
                _Включить автопилот и алерты — кнопки ниже_
                """, user.getFirstName());

        if (!mentorProfileService.isProfileConfigured(user)) {
            welcome += "\n\n⚠️ Профиль не заполнен — «📝 Настроить профиль» (4 шага, ~2 мин).";
        }

        if (!Boolean.TRUE.equals(user.getOnboardingDone())) {
            return new BotResponse(welcome, TelegramKeyboards.mainMenu(),
                    TelegramKeyboards.autopilotOptIn(), true);
        }
        return new BotResponse(welcome, TelegramKeyboards.mainMenu(),
                TelegramKeyboards.startInlineMenu(), true);
    }

    private BotResponse helpMessage() {
        return BotResponse.withMainMenu("""
                📖 *Навигация Ники*
                
                *Кнопки:*
                📋 След. шаг · 📊 Чек-ин · 🎯 Цели · 🧠 Профиль
                💼 Вакансии · 📋 Отклики · 🎤 Собес · 📚 Учёба
                
                *Автопилот:*
                /autopilot on|off — утро 8:00, день 14:00, вечер 21:00 MSK
                /цели · /сброс — цели и сброс памяти
                /job\\_alerts on|off · /job\\_query Java backend
                
                *HH.ru:*
                /connect\\_hh → /hh\\_resumes → кнопка «Откликнуться»
                /applications — история откликов
                🎯 Цели — кнопки для прогресса (0–100%)
                /area 1 — регион HH (1=Москва, пусто=вся РФ)
                """);
    }

    private static boolean parseOnOff(String args) {
        String t = args.trim().toLowerCase(Locale.ROOT);
        return t.equals("on") || t.equals("1") || t.equals("yes") || t.equals("да") || t.equals("вкл");
    }

    private BotResponse connectHh(User user) {
        String url = hhOAuthService.buildTelegramConnectUrl(user.getTelegramId());
        if (url.startsWith("HH_CLIENT")) {
            return BotResponse.withCareerMenu(
                    "⚠️ HH не настроен на сервере.\nДобавь HH\\_CLIENT\\_ID и HH\\_CLIENT\\_SECRET.");
        }
        return BotResponse.withCareerMenuAndInline(
                "🔗 *Подключение HH.ru*\n\n" +
                        "1. Нажми кнопку *ниже* (не старые сообщения)\n" +
                        "2. Войди в HH\n3. Вернись в Telegram",
                TelegramKeyboards.urlButton("🔐 Авторизоваться на HH.ru", url));
    }

    private BotResponse searchJobs(User user, String query) {
        HhService.VacancySearchResult result = hhService.searchVacancies(user, query);
        return buildSearchResponse(result);
    }

    private BotResponse buildSearchResponse(HhService.VacancySearchResult result) {
        InlineKeyboardMarkup inline = result.vacancies().isEmpty()
                ? TelegramKeyboards.jobSearchSuggestions()
                : TelegramKeyboards.vacancyActions(result.vacancies());
        return BotResponse.withInlineAndMenu(hhService.formatSearchResult(result), inline);
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

    @SuppressWarnings("unchecked")
    private static String extractCompany(Map<String, Object> vacancy) {
        Map<String, Object> employer = (Map<String, Object>) vacancy.get("employer");
        return employer != null ? (String) employer.getOrDefault("name", "—") : "—";
    }

    private static String buildVacancyUrl(String vacancyId) {
        return "https://hh.ru/vacancy/" + vacancyId;
    }

    @SuppressWarnings("unchecked")
    private HhService.VacancyDto mapVacancy(Map<String, Object> v, String vacancyId) {
        return new HhService.VacancyDto(
                vacancyId,
                (String) v.getOrDefault("name", "Без названия"),
                extractCompany(v),
                "—", "", "",
                buildVacancyUrl(vacancyId),
                null
        );
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
        if (text.equals(TelegramKeyboards.BTN_APPLICATIONS)) return "/applications";
        if (text.equals(TelegramKeyboards.BTN_INTERVIEW)) return "/interview";
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
            case "/myapps" -> "/applications";
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
                || text.equals(TelegramKeyboards.BTN_APPLICATIONS)
                || text.equals(TelegramKeyboards.BTN_INTERVIEW)
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
