package org.example.citywatch.use_case.saved_locations;

public final class SavedLocationsInputData {
    public final String label;
    public final String latitude;
    public final String longitude;
    public final String radiusMetres;

    public SavedLocationsInputData(String label, String latitude,
                                   String longitude, String radiusMetres) {
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMetres = radiusMetres;
    }
}