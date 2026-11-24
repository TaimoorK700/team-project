package org.example.citywatch.use_case.refresh_reports;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.EffectivePeriod;
import org.example.citywatch.entity.FieldChange;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentUpdate;
import org.example.citywatch.entity.TransitScope;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;

/**
 * Compares two successive complete snapshots from the same source and reports what changed between them.
 */
public class ReportChangeDetector {

    /**
     * Finds NEWLY_OBSERVED, CHANGED, and NO_LONGER_LISTED reports.
     *  - Only in current snapshot: NEWLY_OBSERVD
     *  - In both, with a meaningful field different: CHANGED
     *  - In both, with no meaningful field different: no update
     *  - Only in previous snapshot: NO_LONGER_LISTED
     */
    public List<IncidentUpdate> detect(Optional<IncidentSnapshot> previous, IncidentSnapshot current) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(current);

        //No previous snapshot means first successful refresh, set baseline
        if (previous.isEmpty()) {
            return List.of();
        }

        //Snapshot from different sources are refused, throw IllegalArgumentException
        IncidentSnapshot before = previous.get();
        if (before.getSource() != current.getSource()) {
            throw new IllegalArgumentException("Snapshots come from different sources");
        }

        Instant observedAt = current.getRetrievedAt();

        //Indexing both snapshots by key so lookup is more efficient
        Map<IncidentKey, Incident> previousByKey = byKey(before);
        Map<IncidentKey, Incident> currentByKey = byKey(current);
        List<IncidentUpdate> updates = new ArrayList<>();

        //Every report in the new snapshot is either new or possibly changed
        for (Incident incident : current.getIncidents()) {
            Incident old = previousByKey.get(incident.getKey());
            if (old == null) { //not in old snapshot means it's newly observed
                updates.add(update(incident.getKey(), observedAt,
                        IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of()));
            } else { //in old snapshot means at least 1 field differed
                Map<String, FieldChange> changes = compare(old, incident); //compare method compares meaningful fields
                if (!changes.isEmpty()) {
                    updates.add(update(incident.getKey(), observedAt,
                            IncidentUpdate.Kind.CHANGED, changes));
                }
            }
        }

        //Reports that were listed but now missing
        for (IncidentKey key : previousByKey.keySet()) {
            if (!currentByKey.containsKey(key)) {
                updates.add(update(key, observedAt, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of()));
            }
        }
        return List.copyOf(updates);
    }

    /**
     * Turns a snapshot's list of reports into a map from key to report.
     */
    private static Map<IncidentKey, Incident> byKey(IncidentSnapshot snapshot) {
        Map<IncidentKey, Incident> map = new LinkedHashMap<>();
        for (Incident incident : snapshot.getIncidents()) {
            map.put(incident.getKey(), incident);
        }
        return map;
    }

    /**
     * Compares two versions of the same report field by field.
     * Returns empty if no meaningful fields has changed.
     */
    private static Map<String, FieldChange> compare(Incident old, Incident now) {
        Map<String, Optional<String>> before = meaningfulFields(old);
        Map<String, Optional<String>> after = meaningfulFields(now);
        Map<String, FieldChange> changes = new LinkedHashMap<>();
        for (String field : before.keySet()) {
            Optional<String> oldValue = before.get(field);
            Optional<String> newValue = after.get(field);
            if (!oldValue.equals(newValue)) {
                changes.put(field, new FieldChange(oldValue, newValue));
            }
        }
        return changes;
    }

    /**
     * Lists the fields users care about, each converted to comaprable text.
     */
    private static Map<String, Optional<String>> meaningfulFields(Incident incident) {
        Map<String, Optional<String>> fields = new LinkedHashMap<>();
        fields.put("title", Optional.of(incident.getTitle()));
        fields.put("description", incident.getDescription());
        fields.put("sourceUrl", incident.getSourceUrl());
        fields.put("category", incident.getCategory());
        fields.put("street", incident.getStreet());
        fields.put("coordinates", incident.getCoordinates().map(ReportChangeDetector::coordinatesText));
        fields.put("severity", incident.getSeverity());
        fields.put("status", incident.getStatus());
        fields.put("reportedAt", incident.getReportedAt().map(Instant::toString));
        fields.put("effectivePeriods",
                joined(incident.getEffectivePeriods(), ReportChangeDetector::periodText));
        fields.put("transitScopes",
                joined(incident.getTransitScopes(), ReportChangeDetector::scopeText));
        return fields;
    }

    /**
     * Formats coordinates as text.
     */
    private static String coordinatesText(Coordinates c) {
        return String.format(Locale.ROOT, "%.6f, %.6f", c.getLatitude(), c.getLongitude());
    }

    /**
     * Formats one effective time period.
     */
    private static String periodText(EffectivePeriod p) {
        return p.getStart().map(Instant::toString).orElse("unknown start")
                + " to " + p.getEnd().map(Instant::toString).orElse("no end");
    }

    /**
     * Formats one affected TTC route or stop.
     */
    private static String scopeText(TransitScope s) {
        if (s.isGeneralAdvisory()) {
            return "general advisory";
        }
        return "route " + s.getRouteId().orElse("any") + ", stop " + s.getStopId().orElse("any");
    }

    /**
     * Joins a list into one text value, separated by ";".
     * Each item is converted to text first and the texts are sorted, so the result does not
     * depend on the order the provider listed them in. Each item stays one unit (a scope keeps
     * its route and stop together), and duplicates are kept.
     */
    private static <T> Optional<String> joined(List<T> items, Function<T, String> toText) {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        List<String> texts = new ArrayList<>();
        for (T item : items) {
            texts.add(toText.apply(item));
        }
        texts.sort(null);
        return Optional.of(String.join("; ", texts));
    }

    /**
     * Creates one update record with a random UUID as its ID.
     * The ID must not be derived from the observation time: two refreshes within the same
     * millisecond would otherwise produce the same ID, which the IncidentCache rejects.
     */
    private static IncidentUpdate update(IncidentKey key, Instant observedAt,
                                         IncidentUpdate.Kind kind,
                                         Map<String, FieldChange> changes) {
        String id = UUID.randomUUID().toString();
        return new IncidentUpdate(id, key, observedAt, kind, changes);
    }
}
