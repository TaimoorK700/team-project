package org.example.citywatch.interface_adapter.incident_preferences;

import java.time.Duration;
import java.util.Optional;

import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesInputBoundary;
import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesInputData;

public final class IncidentPreferencesController {
    private final IncidentPreferencesInputBoundary interactor;

    public IncidentPreferencesController(
            IncidentPreferencesInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void load() {
        interactor.load();
    }

    public void save(
            String categories,
            Optional<Duration> maximumAge,
            boolean includeUnknownTimes,
            String proximityWeight,
            String recencyWeight,
            String categoryWeight,
            String severityWeight) {
        interactor.save(new IncidentPreferencesInputData(
                categories,
                maximumAge,
                includeUnknownTimes,
                proximityWeight,
                recencyWeight,
                categoryWeight,
                severityWeight));
    }
}