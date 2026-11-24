package org.example.citywatch.interface_adapter.incident_preferences;

import javax.swing.SwingUtilities;

import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesOutputBoundary;
import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesOutputData;

public final class IncidentPreferencesPresenter
        implements IncidentPreferencesOutputBoundary {
    private final IncidentPreferencesViewModel viewModel;

    public IncidentPreferencesPresenter(
            IncidentPreferencesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void presentPreferences(IncidentPreferencesOutputData output) {
        onEventThread(() -> viewModel.showPreferences(output));
    }

    @Override
    public void presentError(String message) {
        onEventThread(() -> viewModel.showError(message));
    }

    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        }
        else {
            SwingUtilities.invokeLater(action);
        }
    }
}