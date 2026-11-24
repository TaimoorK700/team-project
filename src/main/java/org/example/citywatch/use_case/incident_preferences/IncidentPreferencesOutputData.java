package org.example.citywatch.use_case.incident_preferences;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.example.citywatch.entity.RankingWeights;
import org.example.citywatch.entity.UserPreferences;

public final class IncidentPreferencesOutputData {
    public final Set<String> categories;
    public final Optional<Duration> maximumAge;
    public final boolean includeUnknownTimes;
    public final double proximityWeight;
    public final double recencyWeight;
    public final double categoryWeight;
    public final double severityWeight;
    public final boolean saved;

    public IncidentPreferencesOutputData(
            UserPreferences preferences, boolean saved) {
        categories = Set.copyOf(preferences.getCategories());
        maximumAge = preferences.getMaximumAge();
        includeUnknownTimes = preferences.getIncludeUnknownTimes();

        RankingWeights weights = preferences.getRankingWeights();
        proximityWeight = weights.getProximity();
        recencyWeight = weights.getRecency();
        categoryWeight = weights.getCategory();
        severityWeight = weights.getSeverity();
        this.saved = saved;
    }
}