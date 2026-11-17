package org.example.citywatch.interface_adapter.saved_locations;

import javax.swing.SwingUtilities;

import org.example.citywatch.use_case.saved_locations.SavedLocationsOutputBoundary;
import org.example.citywatch.use_case.saved_locations.SavedLocationsOutputData;

public final class SavedLocationsPresenter implements SavedLocationsOutputBoundary {
    private final SavedLocationsViewModel viewModel;

    public SavedLocationsPresenter(SavedLocationsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void presentLocations(SavedLocationsOutputData output) {
        onEventThread(() -> viewModel.showLocations(output.locations));
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