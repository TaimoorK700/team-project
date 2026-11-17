package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class Coordinates {
    private final double latitude;
    private final double longitude;

    public Coordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid latitude or longitude");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }

    public double getLongitude() { return longitude; }

}
