package org.example.citywatch.use_case.saved_locations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.ProfileRepository;

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
        UserProfile profile;

        try {
            profile = loadProfile();
        }
        catch (DataAccessException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        output.presentLocations(
                new SavedLocationsOutputData(profile.getSavedLocations()));
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
            locations.set(index, createLocation(locationId, input));
        });
    }

    @Override
    public void delete(String locationId) {
        update(locations ->
                locations.remove(findIndex(locations, locationId)));
    }

    private UserProfile loadProfile() throws DataAccessException {
        return repository.load()
                .orElseGet(() -> UserProfile.empty("local"));
    }

    private void update(Consumer<List<SavedLocation>> change) {
        UserProfile updatedProfile;

        try {
            UserProfile currentProfile = loadProfile();

            List<SavedLocation> updatedLocations =
                    new ArrayList<>(currentProfile.getSavedLocations());

            change.accept(updatedLocations);

            // Preserve preferences, subscriptions, bookmarks, and profile ID.
            updatedProfile =
                    currentProfile.withSavedLocations(updatedLocations);

            repository.save(updatedProfile);
        }
        catch (IllegalArgumentException | DataAccessException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        // Update the screen only after saving succeeds.
        output.presentLocations(
                new SavedLocationsOutputData(updatedProfile.getSavedLocations()));
    }

    private static SavedLocation createLocation(
            String id, SavedLocationsInputData input) {
        if (input == null) {
            throw new IllegalArgumentException("Enter the location details.");
        }
        if (input.label == null || input.label.isBlank()) {
            throw new IllegalArgumentException("Enter a location label.");
        }

        double latitude = parseNumber(input.latitude, "Latitude");
        double longitude = parseNumber(input.longitude, "Longitude");

        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180.");
        }

        return new SavedLocation(
                id,
                input.label.strip(),
                new Coordinates(latitude, longitude),
                parseNumber(input.radiusMetres, "Radius"));
    }

    private static double parseNumber(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }

        double number;
        try {
            number = Double.parseDouble(value.strip());
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a number, for example 43.66.");
        }

        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(
                    field + " must be a finite number.");
        }

        return number;
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