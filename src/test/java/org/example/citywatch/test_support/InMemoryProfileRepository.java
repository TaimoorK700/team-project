package org.example.citywatch.test_support;

import java.util.List;

import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.example.citywatch.use_case.gateway.ProfileStorageException;

public final class InMemoryProfileRepository implements ProfileRepository {
    private List<SavedLocation> locations = List.of();
    private boolean failOnSave;

    @Override
    public List<SavedLocation> loadSavedLocations() {
        return List.copyOf(locations);
    }

    @Override
    public void saveSavedLocations(List<SavedLocation> locations) {
        if (failOnSave) {
            throw new ProfileStorageException("Could not save. Please retry.");
        }
        this.locations = List.copyOf(locations);
    }

    public void setFailOnSave(boolean failOnSave) {
        this.failOnSave = failOnSave;
    }
}