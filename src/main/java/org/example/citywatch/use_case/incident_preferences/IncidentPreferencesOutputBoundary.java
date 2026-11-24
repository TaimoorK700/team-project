package org.example.citywatch.use_case.incident_preferences;

public interface IncidentPreferencesOutputBoundary {
    void presentPreferences(IncidentPreferencesOutputData output);

    void presentError(String message);
}