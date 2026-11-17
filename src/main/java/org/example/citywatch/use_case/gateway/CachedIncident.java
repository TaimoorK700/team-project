package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

/** Immutable shared value used by CityWatch. */
public final class CachedIncident {
    private final Incident incident;
    private final boolean listed;
    private final List<IncidentUpdate> updates;

    public CachedIncident(Incident incident, boolean listed, List<IncidentUpdate> updates) {
        Set<String> ids = new HashSet<>();
        for (IncidentUpdate update : updates) {
            if (!update.getIncidentKey().equals(incident.getKey()) || !ids.add(update.getId())) {
                throw new IllegalArgumentException("Invalid report history");
            }
        }
        this.incident = Objects.requireNonNull(incident);
        this.listed = listed;
        this.updates = List.copyOf(updates);
    }

    public Incident getIncident() { return incident; }

    public boolean getListed() { return listed; }

    public List<IncidentUpdate> getUpdates() { return updates; }

}
