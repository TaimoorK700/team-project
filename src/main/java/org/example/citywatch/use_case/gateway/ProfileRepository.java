package org.example.citywatch.use_case.gateway;

import java.util.List;
import org.example.citywatch.entity.SavedLocation;

public interface ProfileRepository {
    List<SavedLocation> loadSavedLocations();

    // A failed save must leave previously saved locations unchanged.
    void saveSavedLocations(List<SavedLocation> locations);
}