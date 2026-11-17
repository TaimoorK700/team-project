package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

/** Immutable shared value used by CityWatch. */
public final class IncidentSnapshot {
    private final IncidentSource source;
    private final Instant retrievedAt;
    private final List<Incident> incidents;

    public IncidentSnapshot(IncidentSource source, Instant retrievedAt, List<Incident> incidents) {
        Set<IncidentKey> keys = new HashSet<>();
        for (Incident incident : incidents) {
            if (incident.getKey().getSource() != source || !keys.add(incident.getKey())) {
                throw new IllegalArgumentException("Wrong source or duplicate report key");
            }
        }
        this.source = Objects.requireNonNull(source);
        this.retrievedAt = Objects.requireNonNull(retrievedAt);
        this.incidents = List.copyOf(incidents);
    }

    public IncidentSource getSource() { return source; }

    public Instant getRetrievedAt() { return retrievedAt; }

    public List<Incident> getIncidents() { return incidents; }

}
