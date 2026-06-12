package com.niki.bot;

import com.niki.model.Goal;
import com.niki.service.HhService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TelegramKeyboards {

    public static final String BTN_NEXT_STEP = "📋 След. шаг";
    public static final String BTN_CHECKIN = "📊 Чек-ин";
    public static final String BTN_GOALS = "🎯 Мои цели";
    public static final String BTN_PROFILE = "🧠 Профиль";
    public static final String BTN_JOBS = "💼 Вакансии";
    public static final String BTN_LEARNING = "📚 Учёба";
    public static final String BTN_SETUP_PROFILE = "📝 Настроить профиль";
    public static final String BTN_CONNECT_HH = "🔗 HH";
    public static final String BTN_RESUMES = "📄 Резюме";
    public static final String BTN_HELP = "❓ Помощь";
    public static final String BTN_APPLICATIONS = "📋 Отклики";
    public static final String BTN_INTERVIEW = "🎤 Собес";

    private TelegramKeyboards() {
    }

    public static ReplyKeyboardMarkup mainMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);
        keyboard.setSelective(false);
        keyboard.setKeyboard(List.of(
                row(BTN_NEXT_STEP, BTN_CHECKIN),
                row(BTN_GOALS, BTN_PROFILE),
                row(BTN_JOBS, BTN_APPLICATIONS),
                row(BTN_LEARNING, BTN_INTERVIEW),
                row(BTN_CONNECT_HH, BTN_HELP)
        ));
        return keyboard;
    }

    public static ReplyKeyboardMarkup careerMenu() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setKeyboard(List.of(
                row(BTN_JOBS, BTN_RESUMES),
                row(BTN_APPLICATIONS, BTN_CONNECT_HH),
                row(BTN_NEXT_STEP, "◀️ Главное меню")
        ));
        return keyboard;
    }

    public static InlineKeyboardMarkup startInlineMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton(BTN_NEXT_STEP, "next_step"),
                        inlineButton(BTN_CHECKIN, "checkin")
                ),
                List.of(
                        inlineButton(BTN_GOALS, "goals"),
                        inlineButton(BTN_PROFILE, "profile")
                ),
                List.of(
                        inlineButton(BTN_SETUP_PROFILE, "setup_profile"),
                        inlineButton(BTN_JOBS, "jobs")
                )
        ));
        return markup;
    }

    public static InlineKeyboardMarkup autopilotOptIn() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton("✅ Автопилот + алерты", "autopilot:on:both"),
                        inlineButton("🔔 Только алерты", "autopilot:on:alerts")
                ),
                List.of(
                        inlineButton("⏸ Без автопилота", "autopilot:off"),
                        inlineButton(BTN_SETUP_PROFILE, "setup_profile")
                )
        ));
        return markup;
    }

    public static InlineKeyboardMarkup profileActions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(inlineButton(BTN_SETUP_PROFILE, "setup_profile")),
                List.of(
                        inlineButton(BTN_GOALS, "goals"),
                        inlineButton(BTN_CHECKIN, "checkin")
                )
        ));
        return markup;
    }

    public static InlineKeyboardMarkup jobSearchSuggestions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton("Java backend", "jobs:Java backend"),
                        inlineButton("Spring Boot", "jobs:Spring Boot developer")
                ),
                List.of(
                        inlineButton("Junior remote", "filter:remote:Junior Java"),
                        inlineButton("Middle backend", "filter:exp:between1And3:Java backend")
                ),
                List.of(
                        inlineButton("Удалёнка", "filter:remote:Java backend"),
                        inlineButton("С зарплатой", "filter:salary:Java backend")
                ),
                List.of(inlineButton("◀️ Главное меню", "main_menu"))
        ));
        return markup;
    }

    public static InlineKeyboardMarkup vacancyActions(List<HhService.VacancyDto> vacancies) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int limit = Math.min(vacancies.size(), 5);
        for (int i = 0; i < limit; i++) {
            HhService.VacancyDto v = vacancies.get(i);
            String shortTitle = v.title().length() > 18 ? v.title().substring(0, 18) + "…" : v.title();
            rows.add(List.of(
                    inlineButton("✉️ " + shortTitle, "apply:" + v.id()),
                    inlineButton("💾", "save:" + v.id()),
                    inlineButton("⏭", "skip:" + v.id())
            ));
        }
        rows.add(List.of(inlineButton("◀️ Меню", "main_menu")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup coverLetterActions(String vacancyId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(inlineButton("✅ Отправить отклик", "confirm:" + vacancyId)),
                List.of(
                        inlineButton("✂️ Короче", "letter:short:" + vacancyId),
                        inlineButton("☕ Spring", "letter:spring:" + vacancyId)
                ),
                List.of(inlineButton("◀️ Отмена", "main_menu"))
        ));
        return markup;
    }

    public static InlineKeyboardMarkup resumePicker(List<Map<String, String>> resumes) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Map<String, String> r : resumes) {
            String title = r.get("title");
            if (title.length() > 28) {
                title = title.substring(0, 28) + "…";
            }
            rows.add(List.of(inlineButton("📄 " + title, "resume:" + r.get("id"))));
        }
        markup.setKeyboard(rows);
        return markup;
    }

    private static KeyboardRow row(String... labels) {
        KeyboardRow row = new KeyboardRow();
        for (String label : labels) {
            row.add(KeyboardButton.builder().text(label).build());
        }
        return row;
    }

    private static InlineKeyboardButton inlineButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    public static InlineKeyboardMarkup goalProgressPicker(List<Goal> goals) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Goal g : goals) {
            String label = truncate(g.getTitle(), 24);
            rows.add(List.of(inlineButton("📈 " + label, "goalpick:" + g.getId())));
        }
        rows.add(List.of(inlineButton("➕ Добавить цель", "addgoal")));
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup goalSetProgress(Long goalId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        inlineButton("0%", "progress:" + goalId + ":0"),
                        inlineButton("25%", "progress:" + goalId + ":25"),
                        inlineButton("50%", "progress:" + goalId + ":50")
                ),
                List.of(
                        inlineButton("75%", "progress:" + goalId + ":75"),
                        inlineButton("✅ 100%", "progress:" + goalId + ":100")
                ),
                List.of(inlineButton("◀️ К целям", "goals"))
        ));
        return markup;
    }

    public static InlineKeyboardMarkup goalsEmptyActions() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(inlineButton("➕ Добавить цель", "addgoal")),
                List.of(inlineButton("◀️ Меню", "main_menu"))
        ));
        return markup;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text != null ? text : "";
        }
        return text.substring(0, max - 1) + "…";
    }

    public static InlineKeyboardMarkup urlButton(String label, String url) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(
                InlineKeyboardButton.builder().text(label).url(url).build()
        )));
        return markup;
    }
}
