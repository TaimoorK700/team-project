package org.example.citywatch.use_case.incident_preferences;

public interface IncidentPreferencesInputBoundary {
    void load();

    void save(IncidentPreferencesInputData input);
}