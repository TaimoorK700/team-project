package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Normalized source report. Retrieval time belongs to IncidentSnapshot, not this entity.
 * Severity/status are source text; adapters must document any normalization.
 */
public final class Incident {
    private final IncidentKey key;
    private final String title;
    private final Optional<String> description;
    private final Optional<String> sourceUrl;
    private final Optional<String> category;
    private final Optional<String> street;
    private final Optional<Coordinates> coordinates;
    private final Optional<String> severity;
    private final Optional<String> status;
    private final Optional<Instant> reportedAt;
    private final Optional<Instant> sourceUpdatedAt;
    private final List<EffectivePeriod> effectivePeriods;
    private final List<TransitScope> transitScopes;
    private Incident(Builder b) {
        key = b.key;
        title = b.title;
        description = b.description;
        sourceUrl = b.sourceUrl;
        category = b.category;
        street = b.street;
        coordinates = b.coordinates;
        severity = b.severity;
        status = b.status;
        reportedAt = b.reportedAt;
        sourceUpdatedAt = b.sourceUpdatedAt;
        effectivePeriods = List.copyOf(b.effectivePeriods);
        transitScopes = List.copyOf(b.transitScopes);
    }
    public IncidentKey getKey() { return key; }
    public String getTitle() { return title; }
    public Optional<String> getDescription() { return description; }
    public Optional<String> getSourceUrl() { return sourceUrl; }
    public Optional<String> getCategory() { return category; }
    public Optional<String> getStreet() { return street; }
    public Optional<Coordinates> getCoordinates() { return coordinates; }
    public Optional<String> getSeverity() { return severity; }
    public Optional<String> getStatus() { return status; }
    public Optional<Instant> getReportedAt() { return reportedAt; }
    public Optional<Instant> getSourceUpdatedAt() { return sourceUpdatedAt; }
    public List<EffectivePeriod> getEffectivePeriods() { return effectivePeriods; }
    public List<TransitScope> getTransitScopes() { return transitScopes; }
    public static Builder builder(IncidentKey key, String title) { return new Builder(key, title); }
    public static final class Builder {
        private IncidentKey key;
        private String title;
        private Optional<String> description = Optional.empty();
        private Optional<String> sourceUrl = Optional.empty();
        private Optional<String> category = Optional.empty();
        private Optional<String> street = Optional.empty();
        private Optional<Coordinates> coordinates = Optional.empty();
        private Optional<String> severity = Optional.empty();
        private Optional<String> status = Optional.empty();
        private Optional<Instant> reportedAt = Optional.empty();
        private Optional<Instant> sourceUpdatedAt = Optional.empty();
        private List<EffectivePeriod> effectivePeriods = List.of();
        private List<TransitScope> transitScopes = List.of();
        private Builder(IncidentKey key, String title) {
            this.key = Objects.requireNonNull(key);
            this.title = Checks.text(title);
        }
        public Builder description(String value) { this.description = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder sourceUrl(String value) { this.sourceUrl = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder category(String value) { this.category = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder street(String value) { this.street = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder coordinates(Coordinates value) { this.coordinates = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder severity(String value) { this.severity = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder status(String value) { this.status = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder reportedAt(Instant value) { this.reportedAt = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder sourceUpdatedAt(Instant value) { this.sourceUpdatedAt = Optional.of(Objects.requireNonNull(value)); return this; }
        public Builder effectivePeriods(List<EffectivePeriod> value) { this.effectivePeriods = List.copyOf(value); return this; }
        public Builder transitScopes(List<TransitScope> value) { this.transitScopes = List.copyOf(value); return this; }
        public Incident build() { return new Incident(this); }
    }
}
