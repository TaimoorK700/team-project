package org.example.citywatch.entity;

public final class SavedLocation {
    private final String id;
    private final String label;
    private final double latitude;
    private final double longitude;
    private final double radiusMetres;

    public SavedLocation(String id, String label, double latitude,
                         double longitude, double radiusMetres) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Location ID is required.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Enter a location label.");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "Latitude must be a finite number between -90 and 90.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Longitude must be a finite number between -180 and 180.");
        }
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException(
                    "Radius must be a finite number greater than zero metres.");
        }

        this.id = id;
        this.label = label.strip();
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMetres = radiusMetres;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getRadiusMetres() { return radiusMetres; }
}