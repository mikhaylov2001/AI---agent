package com.niki.service;

import com.niki.bot.BotResponse;
import com.niki.model.User;
import com.niki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Вакансии/HH — без LLM, чтобы бот не уходил в «нет интернета» и mock-собес.
 */
@Service
@RequiredArgsConstructor
public class JobConversationHandler {

    private final HhService hhService;
    private final UserRepository userRepository;
    private final ConversationFocusService conversationFocusService;

    public Optional<BotResponse> tryHandle(User user, String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        String lower = JobTextPatterns.normalize(text);
        if (JobTextPatterns.isLearningMaterial(text)) {
            return Optional.empty();
        }
        String storedTopic = conversationFocusService.loadTopicId(user);
        boolean jobThread = JobTextPatterns.isJobRelated(lower)
                || ("jobs".equals(storedTopic) && JobTextPatterns.isJobFollowUp(lower))
                || JobTextPatterns.isJobThreadContinuation(lower);

        if (!jobThread) {
            return Optional.empty();
        }

        conversationFocusService.persistTopic(user, "jobs");
        String query = JobTextPatterns.extractSearchQuery(text, user.getJobSearchQuery());
        user.setJobSearchQuery(query);
        userRepository.save(user);

        if (JobTextPatterns.requestsJuniorMiddle(lower) || JobTextPatterns.isJobSearchRequest(lower)) {
            return Optional.of(searchJuniorMiddle(user));
        }
        return Optional.of(searchWithQuery(user, query));
    }

    @Transactional
    BotResponse searchJuniorMiddle(User user) {
        String query = StringUtils.hasText(user.getJobSearchQuery())
                ? user.getJobSearchQuery()
                : "Java backend developer";
        user.setSearchExperience("between1And3");
        userRepository.save(user);

        HhSearchFilters middle = HhSearchFilters.fromUser(user, query, "", hhService.getSearchPerPage())
                .withExperience("between1And3");
        HhService.VacancySearchResult result = hhService.searchVacancies(middle);
        if (!result.vacancies().isEmpty()) {
            return buildSearchResponse(result,
                    "Подборка *Middle* по «" + query + "» — жми ✉️ чтобы откликнуться 👇");
        }

        HhSearchFilters junior = HhSearchFilters.fromUser(user, query + " junior", "", hhService.getSearchPerPage())
                .withExperience("noExperience");
        result = hhService.searchVacancies(junior);
        return buildSearchResponse(result, "Подборка *Junior/Middle* 👇");
    }

    BotResponse searchWithQuery(User user, String query) {
        HhService.VacancySearchResult result = hhService.searchVacancies(user, query);
        String prefix = """
                💼 *Ищу на HH через API* — не браузер, но вакансии реальные.
                
                Запрос: «%s»
                Нажми ✉️ под понравившейся — сгенерирую письмо и отклик.""".formatted(query);
        return buildSearchResponse(result, prefix);
    }

    private BotResponse buildSearchResponse(HhService.VacancySearchResult result, String prefix) {
        if (result.error() != null) {
            return BotResponse.withInlineAndMenu(
                    prefix + "\n\n⚠️ " + result.error() + "\n\nПопробуй /connect\\_hh или позже.",
                    com.niki.bot.TelegramKeyboards.jobSearchSuggestions());
        }
        var inline = result.vacancies().isEmpty()
                ? com.niki.bot.TelegramKeyboards.jobSearchSuggestions()
                : com.niki.bot.TelegramKeyboards.vacancyActions(result.vacancies());
        String body = prefix + "\n\n" + hhService.formatSearchResult(result);
        return BotResponse.withInlineAndMenu(body, inline);
    }
}
