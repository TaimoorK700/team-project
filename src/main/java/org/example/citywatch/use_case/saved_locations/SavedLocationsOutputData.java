package org.example.citywatch.use_case.saved_locations;

import java.util.ArrayList;
import java.util.List;
import org.example.citywatch.entity.SavedLocation;

public final class SavedLocationsOutputData {
    public final List<LocationData> locations;

    public SavedLocationsOutputData(List<SavedLocation> savedLocations) {
        List<LocationData> rows = new ArrayList<>();
        for (SavedLocation location : savedLocations) {
            rows.add(new LocationData(location));
        }
        locations = List.copyOf(rows);
    }

    public static final class LocationData {
        public final String id;
        public final String label;
        public final double latitude;
        public final double longitude;
        public final double radiusMetres;

        private LocationData(SavedLocation location) {
            id = location.getId();
            label = location.getLabel();
            latitude = location.getCoordinates().getLatitude();
            longitude = location.getCoordinates().getLongitude();
            radiusMetres = location.getRadiusMetres();
        }
    }
}