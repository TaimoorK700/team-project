package org.example.citywatch.use_case.saved_locations;

public interface SavedLocationsOutputBoundary {
    void presentLocations(SavedLocationsOutputData output);
    void presentError(String message);
}