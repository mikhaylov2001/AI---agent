package com.niki.service;

import com.niki.model.User;
import com.niki.repository.UserRepository;
import com.niki.util.TelegramLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MentorProfileService {

    private final UserRepository userRepository;

    public static final String PLACEHOLDER = "(ещё не заполнено)";

    private static final List<String> SECTION_ORDER = List.of(
            "ГЛАВНАЯ ЦЕЛЬ",
            "ТЕКУЩИЕ ЦЕЛИ",
            "ГЛАВНЫЕ ПРОБЛЕМЫ",
            "ЧТО ЧАЩЕ ТОРМОЗИТ",
            "КАК ОБЫЧНО СРЫВАЮСЬ",
            "ЧТО ВОЗВРАЩАЕТ В ФОКУС",
            "ВАЖНО ПОМНИТЬ"
    );

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

    @Transactional
    public void mergeIntoProfile(User user, String section, String sourceLabel, String content) {
        ensureDefaultProfile(user);
        ProfileData data = parseProfile(user.getMentorProfile());
        String block = formatMaterialBlock(sourceLabel, content);
        ProfileData updated = switch (section) {
            case "problems" -> data.withProblems(appendSection(data.problems(), block));
            default -> data.withRemember(appendSection(data.remember(), block));
        };
        saveFullProfile(user, updated);
    }

    @Transactional
    public void appendMemoryNote(User user, String sourceLabel, String content) {
        String note = formatMaterialBlock(sourceLabel, content);
        String existing = user.getMemorySummary();
        String merged = StringUtils.hasText(existing)
                ? existing.trim() + "\n\n" + note
                : note;
        if (merged.length() > 8000) {
            merged = merged.substring(merged.length() - 8000);
        }
        user.setMemorySummary(merged);
        userRepository.save(user);
    }

    private static String formatMaterialBlock(String sourceLabel, String content) {
        return "• [" + sourceLabel + "]\n" + content.trim();
    }

    private static String appendSection(String current, String addition) {
        if (!StringUtils.hasText(current) || PLACEHOLDER.equals(current.trim())) {
            return addition;
        }
        if (current.contains(addition.substring(0, Math.min(40, addition.length())))) {
            return current;
        }
        return current.trim() + "\n\n" + addition;
    }

    public String buildDefaultProfile() {
        return formatProfile(ProfileData.builder()
                .mainGoal("Устроиться Java-разработчиком на сильную/высокооплачиваемую работу")
                .currentGoals("""
                        1. Устроиться Java-разработчиком (собесы, резюме, отклики на HH)
                        2. Поддерживать активный поиск работы""")
                .problems(PLACEHOLDER)
                .blockers(PLACEHOLDER)
                .procrastination(PLACEHOLDER)
                .focusRestore(PLACEHOLDER)
                .remember("""
                        - Не любит воду и пустую мотивацию
                        - Нужны структура, честность, конкретика
                        - Раз в 2–3 дня — задание на общение""")
                .build());
    }

    public String formatCoreGoals() {
        return """
                🎯 *Твои цели:*
                
                💻 Устроиться Java-разработчиком
                📋 Активные отклики на HH
                🎯 Прогресс по карьере — жми 🎯 *Мои цели*""";
    }

    public String formatProfile(ProfileData d) {
        return """
                ГЛАВНАЯ ЦЕЛЬ:
                %s

                ТЕКУЩИЕ ЦЕЛИ:
                %s

                ГЛАВНЫЕ ПРОБЛЕМЫ:
                %s

                ЧТО ЧАЩЕ ТОРМОЗИТ:
                %s

                КАК ОБЫЧНО СРЫВАЮСЬ:
                %s

                ЧТО ВОЗВРАЩАЕТ В ФОКУС:
                %s

                ВАЖНО ПОМНИТЬ:
                %s
                """.formatted(
                d.mainGoal(), d.currentGoals(), d.problems(), d.blockers(),
                d.procrastination(), d.focusRestore(), d.remember()
        ).trim();
    }

    @Transactional
    public String formatProfileForDisplay(User user) {
        ensureDefaultProfile(user);
        ProfileData data = parseProfile(user.getMentorProfile());
        String repaired = formatProfile(data);
        if (!repaired.equals(user.getMentorProfile().trim())) {
            user.setMentorProfile(repaired);
            userRepository.save(user);
        }

        StringBuilder sb = new StringBuilder("🧠 *Твой профиль*\n\n");
        appendIfFilled(sb, "🎯", "Главная цель", data.mainGoal());
        appendIfFilled(sb, "📌", "Цели", data.currentGoals());
        appendIfFilled(sb, "⚠️", "Проблемы", data.problems());
        appendIfFilled(sb, "🐢", "Что тормозит", data.blockers());
        appendIfFilled(sb, "💥", "Как срываюсь", data.procrastination());
        appendIfFilled(sb, "🔋", "Что возвращает фокус", data.focusRestore());
        appendIfFilled(sb, "💡", "Важно помнить", data.remember());

        if (sb.length() <= "🧠 *Твой профиль*\n\n".length()) {
            sb.append("_Профиль пуст — нажми «📝 Настроить профиль»_\n\n");
        }
        sb.append("_Обновить:_ «📝 Настроить профиль»");
        return TelegramLimits.truncate(sb.toString().trim(), 3800);
    }

    private static void appendIfFilled(StringBuilder sb, String emoji, String label, String value) {
        if (!hasContent(value)) {
            return;
        }
        sb.append(emoji).append(" *").append(label).append("*\n")
                .append(value.trim()).append("\n\n");
    }

    private static boolean hasContent(String value) {
        return StringUtils.hasText(value) && !PLACEHOLDER.equals(value.trim());
    }

    public String profileSetupQuestion(int step) {
        return switch (step) {
            case 1 -> """
                    📝 *Шаг 1/4 — Главная цель*
                    
                    Сейчас: *Java backend разработчик*.
                    Напиши свою главную цель или «да», если оставляем.""";
            case 2 -> """
                    📝 *Шаг 2/4 — Цели*
                    
                    Напиши текущие цели (списком или через запятую).""";
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
                String[] p = splitIntoTwo(text);
                yield current.withFocusRestore(p[0]).withRemember(p[1]);
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
                .remember(extractSection(raw, "ВАЖНО ПОМНИТЬ"))
                .build();
    }

    private static String extractSection(String raw, String title) {
        int contentStart = sectionContentStart(raw, title);
        if (contentStart < 0) {
            return PLACEHOLDER;
        }
        int contentEnd = nextSectionStart(raw, contentStart, title);
        String value = sanitizeSectionBody(raw.substring(contentStart, contentEnd).trim());
        return value.isEmpty() ? PLACEHOLDER : value;
    }

    private static int sectionContentStart(String raw, String title) {
        Matcher m = sectionHeaderPattern(title).matcher(raw);
        return m.find() ? m.end() : -1;
    }

    private static int nextSectionStart(String raw, int after, String currentTitle) {
        int end = raw.length();
        for (String section : SECTION_ORDER) {
            if (section.equals(currentTitle)) {
                continue;
            }
            Matcher m = Pattern.compile("\\n" + sectionHeaderRegex(section), Pattern.MULTILINE).matcher(raw);
            if (m.find(after) && m.start() < end) {
                end = m.start();
            }
        }
        return end;
    }

    private static Pattern sectionHeaderPattern(String title) {
        return Pattern.compile("(?:^|\\n)" + sectionHeaderRegex(title), Pattern.MULTILINE);
    }

    private static String sectionHeaderRegex(String title) {
        return Pattern.quote(title) + "(?:\\s*\\(\\d+\\))?\\s*:\\s*";
    }

    /** Убираем вложенные заголовки из испорченных профилей. */
    private static String sanitizeSectionBody(String body) {
        StringBuilder clean = new StringBuilder();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!clean.isEmpty() && clean.charAt(clean.length() - 1) != '\n') {
                    clean.append('\n');
                }
                continue;
            }
            if (isSectionHeaderLine(trimmed)) {
                continue;
            }
            if (!clean.isEmpty() && clean.charAt(clean.length() - 1) != '\n') {
                clean.append('\n');
            }
            clean.append(trimmed);
        }
        return clean.toString().trim();
    }

    private static boolean isSectionHeaderLine(String line) {
        for (String section : SECTION_ORDER) {
            if (line.matches("(?i)" + Pattern.quote(section) + "(?:\\s*\\(\\d+\\))?\\s*:?\\s*")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAffirmative(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.equals("да") || t.equals("ok") || t.equals("ок") || t.equals("+")
                || t.equals("оставляем") || t.equals("yes");
    }

    private static String[] splitIntoTwo(String text) {
        String[] lines = text.lines().map(String::trim).filter(StringUtils::hasText).toArray(String[]::new);
        if (lines.length >= 2) {
            int mid = (lines.length + 1) / 2;
            return new String[]{
                    join(lines, 0, mid),
                    join(lines, mid, lines.length)
            };
        }
        if (lines.length == 1) {
            return new String[]{lines[0], PLACEHOLDER};
        }
        return new String[]{PLACEHOLDER, PLACEHOLDER};
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
                    .remember(PLACEHOLDER)
                    .build();
        }

        public ProfileData withMainGoal(String v) {
            return new ProfileData(v, currentGoals, problems, blockers, procrastination, focusRestore, remember);
        }

        public ProfileData withCurrentGoals(String v) {
            return new ProfileData(mainGoal, v, problems, blockers, procrastination, focusRestore, remember);
        }

        public ProfileData withProblems(String v) {
            return new ProfileData(mainGoal, currentGoals, v, blockers, procrastination, focusRestore, remember);
        }

        public ProfileData withBlockers(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, v, procrastination, focusRestore, remember);
        }

        public ProfileData withProcrastination(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, v, focusRestore, remember);
        }

        public ProfileData withFocusRestore(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, procrastination, v, remember);
        }

        public ProfileData withRemember(String v) {
            return new ProfileData(mainGoal, currentGoals, problems, blockers, procrastination, focusRestore, v);
        }

        public static class ProfileDataBuilder {
            private String mainGoal = PLACEHOLDER;
            private String currentGoals = PLACEHOLDER;
            private String problems = PLACEHOLDER;
            private String blockers = PLACEHOLDER;
            private String procrastination = PLACEHOLDER;
            private String focusRestore = PLACEHOLDER;
            private String remember = PLACEHOLDER;

            public ProfileDataBuilder mainGoal(String v) { this.mainGoal = v; return this; }
            public ProfileDataBuilder currentGoals(String v) { this.currentGoals = v; return this; }
            public ProfileDataBuilder problems(String v) { this.problems = v; return this; }
            public ProfileDataBuilder blockers(String v) { this.blockers = v; return this; }
            public ProfileDataBuilder procrastination(String v) { this.procrastination = v; return this; }
            public ProfileDataBuilder focusRestore(String v) { this.focusRestore = v; return this; }
            public ProfileDataBuilder remember(String v) { this.remember = v; return this; }

            public ProfileData build() {
                return new ProfileData(mainGoal, currentGoals, problems, blockers,
                        procrastination, focusRestore, remember);
            }
        }
    }
}
