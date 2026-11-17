package org.example.citywatch.interface_adapter.saved_locations;

import org.example.citywatch.use_case.saved_locations.SavedLocationsInputBoundary;
import org.example.citywatch.use_case.saved_locations.SavedLocationsInputData;

public final class SavedLocationsController {
    private final SavedLocationsInputBoundary interactor;

    public SavedLocationsController(SavedLocationsInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void load() {
        interactor.load();
    }

    public void add(String label, String latitude,
                    String longitude, String radiusMetres) {
        interactor.add(new SavedLocationsInputData(
                label, latitude, longitude, radiusMetres));
    }

    public void edit(String id, String label, String latitude,
                     String longitude, String radiusMetres) {
        interactor.edit(id, new SavedLocationsInputData(
                label, latitude, longitude, radiusMetres));
    }

    public void delete(String id) {
        interactor.delete(id);
    }
}