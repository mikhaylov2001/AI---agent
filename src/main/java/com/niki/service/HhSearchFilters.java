package com.niki.service;

import com.niki.model.User;
import org.springframework.util.StringUtils;

public record HhSearchFilters(
        String query,
        String area,
        String experience,
        boolean remote,
        boolean onlyWithSalary,
        int perPage
) {
    public static HhSearchFilters defaults(String query, String defaultArea, int perPage) {
        return new HhSearchFilters(
                StringUtils.hasText(query) ? query : "Java backend developer",
                defaultArea,
                null,
                false,
                false,
                perPage
        );
    }

    public static HhSearchFilters fromUser(User user, String query, String defaultArea, int perPage) {
        String q = StringUtils.hasText(query) ? query : user.getJobSearchQuery();
        if (!StringUtils.hasText(q)) {
            q = "Java backend developer";
        }
        String area = StringUtils.hasText(user.getHhSearchArea()) ? user.getHhSearchArea() : defaultArea;
        return new HhSearchFilters(
                q,
                area,
                user.getSearchExperience(),
                Boolean.TRUE.equals(user.getSearchRemote()),
                false,
                perPage
        );
    }

    public HhSearchFilters withRemote(boolean remote) {
        return new HhSearchFilters(query, area, experience, remote, onlyWithSalary, perPage);
    }

    public HhSearchFilters withExperience(String exp) {
        return new HhSearchFilters(query, area, exp, remote, onlyWithSalary, perPage);
    }
}
