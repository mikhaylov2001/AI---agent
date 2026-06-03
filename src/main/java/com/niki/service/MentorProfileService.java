package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MentorProfileService {

    private final UserRepository userRepository;

    public static final String PLACEHOLDER = "(ещё не заполнено)";

    public String loadMentorInstructions() {
        try {
            return new ClassPathResource("mentor/instructions.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Ты — Ники, личный наставник. Отвечай конкретно, без воды.";
        }
    }

    public boolean isProfileConfigured(User user) {
        if (!StringUtils.hasText(user.getMentorProfile())) {
            return false;
        }
        return !user.getMentorProfile().contains(PLACEHOLDER);
    }

    @Transactional
    public void ensureDefaultProfile(User user) {
        if (!StringUtils.hasText(user.getMentorProfile())) {
            user.setMentorProfile(buildDefaultProfile());
            userRepository.save(user);
        }
    }

    @Transactional
    public void saveFullProfile(User user, ProfileData data) {
        user.setMentorProfile(formatProfile(data));
        userRepository.save(user);
    }

    public String buildDefaultProfile() {
        return formatProfile(ProfileData.builder()
                .mainGoal("Устроиться на работу Java backend разработчиком")
                .currentGoals("""
                        1. Трудоустройство Java backend (резюме, собесы, pet-проекты)
                        2. Рост в английском
                        3. Дисциплина и продуктивность""")
                .problems(PLACEHOLDER)
                .blockers(PLACEHOLDER)
                .procrastination(PLACEHOLDER)
                .focusRestore(PLACEHOLDER)
                .learningNow("Java backend, Spring Boot, проекты svoi-mastera / niki-bot / FinTracker")
                .remember("""
                        - Не любит воду и пустую мотивацию
                        - Нужны структура, честность, конкретика
                        - Главный приоритет — работа Java backend""")
                .build());
    }

    public String formatProfile(ProfileData d) {
        return """
                ГЛАВНАЯ ЦЕЛЬ:
                %s

                ТЕКУЩИЕ ЦЕЛИ (3):
                %s

                ГЛАВНЫЕ ПРОБЛЕМЫ:
                %s

                ЧТО ЧАЩЕ ТОРМОЗИТ:
                %s

                КАК ОБЫЧНО СРЫВАЮСЬ:
                %s

                ЧТО ВОЗВРАЩАЕТ В ФОКУС:
                %s

                ЧЕМУ УЧУСЬ СЕЙЧАС:
                %s

                ВАЖНО ПОМНИТЬ:
                %s
                """.formatted(
                d.mainGoal(), d.currentGoals(), d.problems(), d.blockers(),
                d.procrastination(), d.focusRestore(), d.learningNow(), d.remember()
        ).trim();
    }

    public String formatProfileForDisplay(User user) {
        ensureDefaultProfile(user);
        return "🧠 *Твой профиль*\n\n" + user.getMentorProfile()
                + "\n\n_Обновить:_ «📝 Настроить профиль»";
    }

    public String profileSetupQuestion(int step) {
        return switch (step) {
            case 1 -> """
                    📝 *Шаг 1/4 — Главная цель*
                    
                    Сейчас: *Java backend разработчик*.
                    Напиши свою главную цель или «да», если оставляем.""";
            case 2 -> """
                    📝 *Шаг 2/4 — Три цели*
                    
                    Напиши 3 текущие цели (списком или через запятую).""";
            case 3 -> """
                    📝 *Шаг 3/4 — Проблемы*
                    
                    Одним сообщением (можно списком):
                    • 3 главные проблемы
                    • что тормозит
                    • как срываешься""";
            case 4 -> """
                    📝 *Шаг 4/4 — Фокус*
                    
                    Одним сообщением:
                    • что возвращает в фокус
                    • чему учишься
                    • что важно помнить о тебе""";
            default -> """
                    ✅ *Профиль сохранён*
                    
                    Используй кнопки внизу:
                    📋 След. шаг · 📊 Чек-ин · 🎯 Цели · 💼 Вакансии""";
        };
    }

    @Transactional
    public String applySetupStep(User user, int step, String answer) {
        ProfileData current = parseProfile(user.getMentorProfile());
        String text = answer.trim();

        ProfileData updated = switch (step) {
            case 1 -> current.withMainGoal(isAffirmative(text) ? current.mainGoal() : text);
            case 2 -> current.withCurrentGoals(text);
            case 3 -> {
                String[] p = splitIntoThree(text);
                yield current.withProblems(p[0]).withBlockers(p[1]).withProcrastination(p[2]);
            }
            case 4 -> {
                String[] p = splitIntoThree(text);
                yield current.withFocusRestore(p[0]).withLearningNow(p[1]).withRemember(p[2]);
            }
            default -> current;
        };

        saveFullProfile(user, updated);
        return profileSetupQuestion(step + 1);
    }

    private ProfileData parseProfile(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ProfileData.fromDefaults();
        }
        return ProfileData.builder()
                .mainGoal(extractSection(raw, "ГЛАВНАЯ ЦЕЛЬ"))
                .currentGoals(extractSection(raw, "ТЕКУЩИЕ ЦЕЛИ"))
                .problems(extractSection(raw, "ГЛАВНЫЕ ПРОБЛЕМЫ"))
                .blockers(extractSection(raw, "ЧТО ЧАЩЕ ТОРМОЗИТ"))
                .procrastination(extractSection(raw, "КАК ОБЫЧНО СРЫВАЮСЬ"))
                .focusRestore(extractSection(raw, "ЧТО ВОЗВРАЩАЕТ В ФОКУС"))
                .learningNow(extractSection(raw, "ЧЕМУ УЧУСЬ СЕЙЧАС"))
                .remember(extractSection(raw, "ВАЖНО ПОМНИТЬ"))
                .build();
    }

    private static String extractSection(String raw, String title) {
        int start = raw.indexOf(title + ":");
        if (start < 0) {
            return PLACEHOLDER;
        }
        start = raw.indexOf('\n', start) + 1;
        int end = raw.length();
        for (String next : new String[]{
                "ГЛАВНАЯ ЦЕЛЬ", "ТЕКУЩИЕ ЦЕЛИ", "ГЛАВНЫЕ ПРОБЛЕМЫ", "ЧТО ЧАЩЕ ТОРМОЗИТ",
                "КАК ОБЫЧНО СРЫВАЮСЬ", "ЧТО ВОЗВРАЩАЕТ В ФОКУС", "ЧЕМУ УЧУСЬ СЕЙЧАС", "ВАЖНО ПОМНИТЬ"
        }) {
            if (next.equals(title)) {
                continue;
            }
            int idx = raw.indexOf("\n" + next + ":", start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        String value = raw.substring(start, end).trim();
        return value.isEmpty() ? PLACEHOLDER : value;
    }

    private static boolean isAffirmative(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.equals("да") || t.equals("ok") || t.equals("ок") || t.equals("+")
                || t.equals("оставляем") || t.equals("yes");
    }

    private static String[] splitIntoThree(String text) {
        String[] lines = text.lines().map(String::trim).filter(StringUtils::hasText).toArray(String[]::new);
        if (lines.length >= 3) {
            int third = (lines.length + 2) / 3;
            return new String[]{
                    join(lines, 0, third),
                    join(lines, third, third * 2),
                    join(lines, third * 2, lines.length)
            };
        }
        if (lines.length == 2) {
            return new String[]{lines[0], lines[1], PLACEHOLDER};
        }
        if (lines.length == 1) {
            return new String[]{lines[0], PLACEHOLDER, PLACEHOLDER};
        }
        return new String[]{PLACEHOLDER, PLACEHOLDER, PLACEHOLDER};
    }

    private static String join(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(to, lines.length); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    public record ProfileData(
            String mainGoal,
            String currentGoals,
            String problems,
            String blockers,
            String procrastination,
            String focusRestore,
            String learningNow,
            String remember
    ) {
        public static ProfileDataBuilder builder() {
            return new ProfileDataBuilder();
        }

        public static ProfileData fromDefaults() {
            return builder()
                    .mainGoal("Устроиться на работу Java backend разработчиком")
                    .currentGoals(PLACEHOLDER)
                    .problems(PLACEHOLDER)
                    .blockers(PLACEHOLDER)
                    .procrastination(PLACEHOLDER)
                    .focusRestore(PLACEHOLDER)
                    .learningNow(PLACEHOLDER)
                    .remember(PLACEHOLDER)
                    .build();
        }

        public ProfileData withMainGoal(String v) {
            return new ProfileData(v, currentGoals, problems, blockers, procrastination, focusRestore, learningNow, remember);
        }

        public ProfileData withCurrentGoals(String v) {
            return new ProfileData(mainGoal, v, problems, blockers, procrastination, focusRestore, learningNow, remember);
        }

        public ProfileData withProblems(String v) {
            return new ProfileData(mainGoal, currentGoals, v, blockers, procrastination, focusRestore, learningNow, remember);
        }

        public ProfileData withBlockers(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, v, procrastination, focusRestore, learningNow, remember);
        }

        public ProfileData withProcrastination(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, v, focusRestore, learningNow, remember);
        }

        public ProfileData withFocusRestore(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, procrastination, v, learningNow, remember);
        }

        public ProfileData withLearningNow(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, procrastination, focusRestore, v, remember);
        }

        public ProfileData withRemember(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, procrastination, focusRestore, learningNow, v);
        }

        public static class ProfileDataBuilder {
            private String mainGoal = PLACEHOLDER;
            private String currentGoals = PLACEHOLDER;
            private String problems = PLACEHOLDER;
            private String blockers = PLACEHOLDER;
            private String procrastination = PLACEHOLDER;
            private String focusRestore = PLACEHOLDER;
            private String learningNow = PLACEHOLDER;
            private String remember = PLACEHOLDER;

            public ProfileDataBuilder mainGoal(String v) { this.mainGoal = v; return this; }
            public ProfileDataBuilder currentGoals(String v) { this.currentGoals = v; return this; }
            public ProfileDataBuilder problems(String v) { this.problems = v; return this; }
            public ProfileDataBuilder blockers(String v) { this.blockers = v; return this; }
            public ProfileDataBuilder procrastination(String v) { this.procrastination = v; return this; }
            public ProfileDataBuilder focusRestore(String v) { this.focusRestore = v; return this; }
            public ProfileDataBuilder learningNow(String v) { this.learningNow = v; return this; }
            public ProfileDataBuilder remember(String v) { this.remember = v; return this; }

            public ProfileData build() {
                return new ProfileData(mainGoal, currentGoals, problems, blockers,
                        procrastination, focusRestore, learningNow, remember);
            }
        }
    }
}
