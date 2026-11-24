package org.example.citywatch.interface_adapter.incident_preferences;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesOutputData;

public final class IncidentPreferencesViewModel {
    private final PropertyChangeSupport changes =
            new PropertyChangeSupport(this);

    private IncidentPreferencesOutputData preferences;
    private String error = "";

    public IncidentPreferencesOutputData getPreferences() {
        return preferences;
    }

    public String getError() {
        return error;
    }

    public void showPreferences(IncidentPreferencesOutputData preferences) {
        this.preferences = preferences;
        error = "";
        changes.firePropertyChange("preferences", null, preferences);
    }

    public void showError(String message) {
        error = message == null || message.isBlank()
                ? "Could not complete the operation. Please retry."
                : message;

        changes.firePropertyChange("error", null, error);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }
}