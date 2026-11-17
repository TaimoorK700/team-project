package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class UserPreferences {
    private final Set<String> categories;
    private final Optional<Duration> maximumAge;
    private final boolean includeUnknownTimes;
    private final RankingWeights rankingWeights;

    public UserPreferences(Set<String> categories, Optional<Duration> maximumAge, boolean includeUnknownTimes, RankingWeights rankingWeights) {
        categories.forEach(Checks::text);
        maximumAge.ifPresent(age -> {
            if (age.isNegative() || age.isZero()) throw new IllegalArgumentException("Age must be positive");
        });
        this.categories = Set.copyOf(categories);
        this.maximumAge = Objects.requireNonNull(maximumAge);
        this.includeUnknownTimes = includeUnknownTimes;
        this.rankingWeights = Objects.requireNonNull(rankingWeights);
    }

    public Set<String> getCategories() { return categories; }

    public Optional<Duration> getMaximumAge() { return maximumAge; }

    public boolean getIncludeUnknownTimes() { return includeUnknownTimes; }

    public RankingWeights getRankingWeights() { return rankingWeights; }

    /** Proposed defaults: all categories, no age limit; team can revise centrally. */
    public static UserPreferences defaults() {
        return new UserPreferences(Set.of(), Optional.empty(), true,
                new RankingWeights(0.5, 0.3, 0.15, 0.05));
    }
    /** An empty category selection means all categories. */
    public boolean acceptsCategory(String category) {
        return categories.isEmpty() || categories.contains(category);
    }

}
