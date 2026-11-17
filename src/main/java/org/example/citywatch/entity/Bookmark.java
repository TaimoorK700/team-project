package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class Bookmark {
    private final IncidentKey incidentKey;
    private final Instant savedAt;
    private final Optional<String> lastReviewedUpdateId;

    public Bookmark(IncidentKey incidentKey, Instant savedAt, Optional<String> lastReviewedUpdateId) {
        lastReviewedUpdateId.ifPresent(Checks::text);
        this.incidentKey = Objects.requireNonNull(incidentKey);
        this.savedAt = Objects.requireNonNull(savedAt);
        this.lastReviewedUpdateId = Objects.requireNonNull(lastReviewedUpdateId);
    }

    public IncidentKey getIncidentKey() { return incidentKey; }

    public Instant getSavedAt() { return savedAt; }

    public Optional<String> getLastReviewedUpdateId() { return lastReviewedUpdateId; }

    public Bookmark reviewedThrough(String updateId) {
        return new Bookmark(incidentKey, savedAt, Optional.of(Checks.text(updateId)));
    }

}
