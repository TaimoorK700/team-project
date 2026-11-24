package org.example.citywatch.use_case.incident_preferences;

import java.time.Duration;
import java.util.Optional;

public final class IncidentPreferencesInputData {
    public final String categories;
    public final Optional<Duration> maximumAge;
    public final boolean includeUnknownTimes;
    public final String proximityWeight;
    public final String recencyWeight;
    public final String categoryWeight;
    public final String severityWeight;

    public IncidentPreferencesInputData(
            String categories,
            Optional<Duration> maximumAge,
            boolean includeUnknownTimes,
            String proximityWeight,
            String recencyWeight,
            String categoryWeight,
            String severityWeight) {
        this.categories = categories;
        this.maximumAge = maximumAge;
        this.includeUnknownTimes = includeUnknownTimes;
        this.proximityWeight = proximityWeight;
        this.recencyWeight = recencyWeight;
        this.categoryWeight = categoryWeight;
        this.severityWeight = severityWeight;
    }
}