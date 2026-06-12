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
 * Вакансии/HH — без LLM, чтобы бот не уходил в собес или выдуманные «30%».
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
        if (!JobTextPatterns.isJobRelated(lower)) {
            return Optional.empty();
        }

        conversationFocusService.persistTopic(user, "jobs");

        if (JobTextPatterns.wantsBotToSearch(lower)) {
            return Optional.of(searchWithPreferences(user, lower));
        }
        if (JobTextPatterns.requestsJuniorMiddle(lower) || JobTextPatterns.isJobSearchRequest(lower)) {
            return Optional.of(searchJuniorMiddle(user));
        }
        return Optional.empty();
    }

    @Transactional
    BotResponse searchJuniorMiddle(User user) {
        String query = StringUtils.hasText(user.getJobSearchQuery())
                ? user.getJobSearchQuery()
                : "Java backend developer";
        user.setJobSearchQuery(query);
        user.setSearchExperience("between1And3");
        userRepository.save(user);

        HhSearchFilters middle = HhSearchFilters.fromUser(user, query, "", hhService.getSearchPerPage())
                .withExperience("between1And3");
        HhService.VacancySearchResult result = hhService.searchVacancies(middle);
        if (!result.vacancies().isEmpty()) {
            return buildSearchResponse(result, "Подборка *Middle* по запросу «" + query + "» 👇");
        }

        HhSearchFilters junior = HhSearchFilters.fromUser(user, query + " junior", "", hhService.getSearchPerPage())
                .withExperience("noExperience");
        result = hhService.searchVacancies(junior);
        return buildSearchResponse(result, "Подборка *Junior/Middle* 👇");
    }

    private BotResponse searchWithPreferences(User user, String lower) {
        if (JobTextPatterns.requestsJuniorMiddle(lower)) {
            return searchJuniorMiddle(user);
        }
        String query = StringUtils.hasText(user.getJobSearchQuery())
                ? user.getJobSearchQuery()
                : "Java backend developer";
        HhService.VacancySearchResult result = hhService.searchVacancies(user, query);
        return buildSearchResponse(result,
                "Я не открываю браузер, но *сам ищу на HH* и даю кнопки отклика 👇");
    }

    private BotResponse buildSearchResponse(HhService.VacancySearchResult result, String prefix) {
        var inline = result.vacancies().isEmpty()
                ? com.niki.bot.TelegramKeyboards.jobSearchSuggestions()
                : com.niki.bot.TelegramKeyboards.vacancyActions(result.vacancies());
        String body = prefix + "\n\n" + hhService.formatSearchResult(result);
        return BotResponse.withInlineAndMenu(body, inline);
    }
}
