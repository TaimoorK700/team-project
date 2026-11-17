package org.example.citywatch.test_support;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.*;

/** Synthetic examples only; these IDs and coordinates do not verify live API fields. */
public final class SampleReports {
    private SampleReports() { }
    public static final Instant FIRST = Instant.parse("2026-11-17T12:00:00Z");
    public static final Instant SECOND = FIRST.plusSeconds(3600);
    public static final IncidentKey ROAD_KEY = new IncidentKey(IncidentSource.TORONTO_ROAD, "123");
    public static final IncidentKey TTC_KEY = new IncidentKey(IncidentSource.TTC, "123");
    public static Incident road(String description) {
        return Incident.builder(ROAD_KEY, "Sample road restriction")
                .description(description).category("CONSTRUCTION")
                .coordinates(new Coordinates(43.6629, -79.3957))
                .street("Sample street").reportedAt(FIRST).build();
    }
    public static IncidentSnapshot roadBefore() {
        return new IncidentSnapshot(IncidentSource.TORONTO_ROAD, FIRST,
                List.of(road("One lane closed")));
    }
    public static IncidentSnapshot roadAfter() {
        return new IncidentSnapshot(IncidentSource.TORONTO_ROAD, SECOND,
                List.of(road("Two lanes closed")));
    }
    public static IncidentSnapshot ttc() {
        Incident alert = Incident.builder(TTC_KEY, "Sample transit disruption")
                .transitScopes(List.of(new TransitScope(Optional.of("route-A"), Optional.of("stop-A")),
                        new TransitScope(Optional.of("route-B"), Optional.of("stop-B"))))
                .effectivePeriods(List.of(new EffectivePeriod(Optional.of(FIRST), Optional.empty())))
                .build();
        return new IncidentSnapshot(IncidentSource.TTC, FIRST, List.of(alert));
    }
    public static IncidentUpdate roadChange() {
        return new IncidentUpdate("update-1", ROAD_KEY, SECOND, IncidentUpdate.Kind.CHANGED,
                Map.of("description", new FieldChange(Optional.of("One lane closed"),
                        Optional.of("Two lanes closed"))));
    }
}
