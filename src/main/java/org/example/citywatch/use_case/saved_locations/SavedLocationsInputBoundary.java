package org.example.citywatch.use_case.saved_locations;

public interface SavedLocationsInputBoundary {
    void load();
    void add(SavedLocationsInputData input);
    void edit(String locationId, SavedLocationsInputData input);
    void delete(String locationId);
}