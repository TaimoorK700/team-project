package org.example.citywatch.interface_adapter.saved_locations;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

import org.example.citywatch.use_case.saved_locations.SavedLocationsOutputData.LocationData;

public final class SavedLocationsViewModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private List<LocationData> locations = List.of();
    private String error = "";

    public List<LocationData> getLocations() {
        return locations;
    }

    public String getError() {
        return error;
    }

    public void showLocations(List<LocationData> locations) {
        this.locations = List.copyOf(locations);
        error = "";
        changes.firePropertyChange("locations", null, this.locations);
    }

    public void showError(String message) {
        error = message;
        changes.firePropertyChange("error", null, message);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }
}