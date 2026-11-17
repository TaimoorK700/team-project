package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class TransitScope {
    private final Optional<String> routeId;
    private final Optional<String> stopId;

    public TransitScope(Optional<String> routeId, Optional<String> stopId) {
        Objects.requireNonNull(routeId).ifPresent(Checks::text);
        Objects.requireNonNull(stopId).ifPresent(Checks::text);
        this.routeId = routeId;
        this.stopId = stopId;
    }

    public Optional<String> getRouteId() { return routeId; }

    public Optional<String> getStopId() { return stopId; }

    /** Empty identifiers represent a broad advisory. A pair stays grouped. */
    public boolean isGeneralAdvisory() { return !routeId.isPresent() && !stopId.isPresent(); }

}
