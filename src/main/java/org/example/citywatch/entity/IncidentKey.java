package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class IncidentKey {
    private final IncidentSource source;
    private final String sourceRecordId;

    public IncidentKey(IncidentSource source, String sourceRecordId) {

        this.source = Objects.requireNonNull(source);
        this.sourceRecordId = Checks.text(sourceRecordId);
    }

    public IncidentSource getSource() { return source; }

    public String getSourceRecordId() { return sourceRecordId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IncidentKey)) return false;
        IncidentKey key = (IncidentKey) other;
        return source == key.source && sourceRecordId.equals(key.sourceRecordId);
    }
    @Override public int hashCode() { return Objects.hash(source, sourceRecordId); }
    @Override public String toString() { return source + ":" + sourceRecordId; }

}
