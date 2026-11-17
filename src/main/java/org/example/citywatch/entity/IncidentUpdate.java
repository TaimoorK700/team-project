package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class IncidentUpdate {
    private final String id;
    private final IncidentKey incidentKey;
    private final Instant observedAt;
    private final Kind kind;
    private final Map<String, FieldChange> changedFields;

    public IncidentUpdate(String id, IncidentKey incidentKey, Instant observedAt, Kind kind, Map<String, FieldChange> changedFields) {
        changedFields.keySet().forEach(Checks::text);
        if (kind == Kind.CHANGED && changedFields.isEmpty()) {
            throw new IllegalArgumentException("Changed report needs field differences");
        }
        this.id = Checks.text(id);
        this.incidentKey = Objects.requireNonNull(incidentKey);
        this.observedAt = Objects.requireNonNull(observedAt);
        this.kind = Objects.requireNonNull(kind);
        this.changedFields = Map.copyOf(changedFields);
    }

    public String getId() { return id; }

    public IncidentKey getIncidentKey() { return incidentKey; }

    public Instant getObservedAt() { return observedAt; }

    public Kind getKind() { return kind; }

    public Map<String, FieldChange> getChangedFields() { return changedFields; }

    public enum Kind { NEWLY_OBSERVED, CHANGED, NO_LONGER_LISTED, RELISTED }

}
