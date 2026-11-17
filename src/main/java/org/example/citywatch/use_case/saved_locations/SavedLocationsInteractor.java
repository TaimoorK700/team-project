package org.example.citywatch.use_case.saved_locations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.example.citywatch.use_case.gateway.ProfileStorageException;

public final class SavedLocationsInteractor implements SavedLocationsInputBoundary {
    private final ProfileRepository repository;
    private final SavedLocationsOutputBoundary output;

    public SavedLocationsInteractor(ProfileRepository repository,
                                    SavedLocationsOutputBoundary output) {
        this.repository = repository;
        this.output = output;
    }

    @Override
    public void load() {
        List<SavedLocation> locations;

        try {
            locations = repository.loadSavedLocations();
        }
        catch (ProfileStorageException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        output.presentLocations(new SavedLocationsOutputData(locations));
    }

    @Override
    public void add(SavedLocationsInputData input) {
        update(locations -> locations.add(
                createLocation(UUID.randomUUID().toString(), input)));
    }

    @Override
    public void edit(String locationId, SavedLocationsInputData input) {
        update(locations -> {
            int index = findIndex(locations, locationId);

            // Reuse the existing ID when replacing the location.
            locations.set(index, createLocation(locationId, input));
        });
    }

    @Override
    public void delete(String locationId) {
        update(locations ->
                locations.remove(findIndex(locations, locationId)));
    }

    private void update(Consumer<List<SavedLocation>> change) {
        List<SavedLocation> updated;

        try {
            // Work on a copy so a failed save cannot modify stored state.
            updated = new ArrayList<>(repository.loadSavedLocations());
            change.accept(updated);
            repository.saveSavedLocations(updated);
        }
        catch (IllegalArgumentException | ProfileStorageException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        // Publish the new list only after persistence succeeds.
        output.presentLocations(new SavedLocationsOutputData(updated));
    }

    private static SavedLocation createLocation(
            String id, SavedLocationsInputData input) {
        if (input == null) {
            throw new IllegalArgumentException("Enter the location details.");
        }
        if (input.label == null || input.label.isBlank()) {
            throw new IllegalArgumentException("Enter a location label.");
        }

        return new SavedLocation(
                id,
                input.label,
                parseNumber(input.latitude, "Latitude"),
                parseNumber(input.longitude, "Longitude"),
                parseNumber(input.radiusMetres, "Radius")
        );
    }

    private static double parseNumber(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }

        try {
            return Double.parseDouble(value.strip());
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a number, for example 43.66.");
        }
    }

    private static int findIndex(List<SavedLocation> locations, String id) {
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getId().equals(id)) {
                return i;
            }
        }

        throw new IllegalArgumentException(
                "That location no longer exists. Reload and select a location.");
    }
}