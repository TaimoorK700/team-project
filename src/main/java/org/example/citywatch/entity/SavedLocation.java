package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class SavedLocation {
    private final String id;
    private final String label;
    private final Coordinates coordinates;
    private final double radiusMetres;

    public SavedLocation(String id, String label, Coordinates coordinates, double radiusMetres) {
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException("Radius must be finite and positive");
        }
        this.id = Checks.text(id);
        this.label = Checks.text(label);
        this.coordinates = Objects.requireNonNull(coordinates);
        this.radiusMetres = radiusMetres;
    }

    public String getId() { return id; }

    public String getLabel() { return label; }

    public Coordinates getCoordinates() { return coordinates; }

    public double getRadiusMetres() { return radiusMetres; }

}
