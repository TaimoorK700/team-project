package org.example.citywatch.use_case.refresh_reports;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.citywatch.entity.EffectivePeriod;
import org.example.citywatch.entity.FieldChange;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.IncidentUpdate;
import org.example.citywatch.entity.TransitScope;
import org.example.citywatch.test_support.InMemoryIncidentCache;
import org.example.citywatch.test_support.SampleReports;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;
import org.junit.jupiter.api.Test;

public class ReportChangeDetectorTest {
    private static final IncidentKey OTHER_ROAD_KEY =
            new IncidentKey(IncidentSource.TORONTO_ROAD, "456");

    private final ReportChangeDetector detector = new ReportChangeDetector();

    /**
     * Tests that the first successful snpashot only establishes baseline and doesn't record any updates.
     */
    @Test
    void firstSnapshotIsBaselineWithNoUpdates() {
        List<IncidentUpdate> updates = detector.detect(Optional.empty(), SampleReports.roadBefore());
        assertTrue(updates.isEmpty());
    }

    /**
     * Tests that identical report content records no updates, even though retrieval time was different.
     */
    @Test
    void unchangedReportProducesNoUpdate() {
        IncidentSnapshot previous = roadSnapshot(SampleReports.FIRST, SampleReports.road("One lane closed"));
        IncidentSnapshot current = roadSnapshot(SampleReports.SECOND, SampleReports.road("One lane closed"));
        List<IncidentUpdate> updates = detector.detect(Optional.of(previous), current);
        assertTrue(updates.isEmpty());
    }

    /**
     * Tests that a report that first appears after baseline is recorded as NEWLY_OBSERVED,
     * and unchanged reports produce no updates.
     */
    @Test
    void newReportIsNewlyObserved() {
        IncidentSnapshot current = roadSnapshot(SampleReports.SECOND, SampleReports.road("One lane closed"), otherRoadReport());
        List<IncidentUpdate> updates = detector.detect(Optional.of(SampleReports.roadBefore()), current);
        IncidentUpdate update = onlyUpdate(updates);
        assertEquals(IncidentUpdate.Kind.NEWLY_OBSERVED, update.getKind());
        assertEquals(OTHER_ROAD_KEY, update.getIncidentKey());
        assertEquals(SampleReports.SECOND, update.getObservedAt());
    }

    /**
     * Tests that a changed field is recorded with the report key, the current
     * snapshot's retrieval time, and the field's previous and new values.
     */
    @Test
    void changedDescriptionRecordsKeyTimeAndValues() {
        List<IncidentUpdate> updates = detector.detect(
                Optional.of(SampleReports.roadBefore()), SampleReports.roadAfter());

        IncidentUpdate update = onlyUpdate(updates);
        assertEquals(IncidentUpdate.Kind.CHANGED, update.getKind());
        assertEquals(SampleReports.ROAD_KEY, update.getIncidentKey());
        assertEquals(SampleReports.SECOND, update.getObservedAt());
        assertEquals(Set.of("description"), update.getChangedFields().keySet());

        FieldChange change = update.getChangedFields().get("description");
        assertEquals(Optional.of("One lane closed"), change.getPreviousValue());
        assertEquals(Optional.of("Two lanes closed"), change.getNewValue());
    }

    /**
     * Tests that a report missing from a later complete snapshot is recorded as no
     * longer listed, rather than as a change to its status.
     */
    @Test
    void missingReportIsNoLongerListed() {
        IncidentSnapshot current = roadSnapshot(SampleReports.SECOND);

        List<IncidentUpdate> updates =
                detector.detect(Optional.of(SampleReports.roadBefore()), current);

        IncidentUpdate update = onlyUpdate(updates);
        assertEquals(IncidentUpdate.Kind.NO_LONGER_LISTED, update.getKind());
        assertEquals(SampleReports.ROAD_KEY, update.getIncidentKey());
        assertEquals(SampleReports.SECOND, update.getObservedAt());
        assertTrue(update.getChangedFields().isEmpty());
    }

    /**
     * Tests taht reports from different sources are never compared, even when their
     * provider record IDs are identical.
     */
    @Test
    void sameRecordIdFromDifferentSourcesIsNotCompared() {
        assertEquals(SampleReports.ROAD_KEY.getSourceRecordId(),
                SampleReports.TTC_KEY.getSourceRecordId());

        assertThrows(IllegalArgumentException.class, () -> detector.detect(
                Optional.of(SampleReports.roadBefore()), SampleReports.ttc()));
    }

    /**
     * Tests that the same transit scopes listed in a different order produce no update.
     */
    @Test
    void reorderedTransitScopesProduceNoUpdate() {
        TransitScope a = scope("route-A", "stop-A");
        TransitScope b = scope("route-B", "stop-B");
        IncidentSnapshot previous = ttcSnapshot(SampleReports.FIRST, List.of(a, b), List.of());
        IncidentSnapshot current = ttcSnapshot(SampleReports.SECOND, List.of(b, a), List.of());
        assertTrue(detector.detect(Optional.of(previous), current).isEmpty());
    }

    /**
     * Tests that the same effective periods listed in a different order produce no update.
     */
    @Test
    void reorderedEffectivePeriodsProduceNoUpdate() {
        EffectivePeriod early = new EffectivePeriod(Optional.of(SampleReports.FIRST),
                Optional.of(SampleReports.SECOND));
        EffectivePeriod late = new EffectivePeriod(Optional.of(SampleReports.SECOND.plusSeconds(3600)),
                Optional.empty());
        IncidentSnapshot previous = ttcSnapshot(SampleReports.FIRST, List.of(), List.of(early, late));
        IncidentSnapshot current = ttcSnapshot(SampleReports.SECOND, List.of(), List.of(late, early));
        assertTrue(detector.detect(Optional.of(previous), current).isEmpty());
    }

    /**
     * Tests that each route/stop pair is compared as a unit: swapping which stop belongs to
     * which route is a real change, even though the same routes and stops appear.
     */
    @Test
    void reshuffledRouteStopPairsAreAChange() {
        IncidentSnapshot previous = ttcSnapshot(SampleReports.FIRST,
                List.of(scope("route-A", "stop-A"), scope("route-B", "stop-B")), List.of());
        IncidentSnapshot current = ttcSnapshot(SampleReports.SECOND,
                List.of(scope("route-A", "stop-B"), scope("route-B", "stop-A")), List.of());
        IncidentUpdate update = onlyUpdate(detector.detect(Optional.of(previous), current));
        assertEquals(IncidentUpdate.Kind.CHANGED, update.getKind());
        assertEquals(Set.of("transitScopes"), update.getChangedFields().keySet());
    }

    /**
     * Regression test: two successive changes whose retrieval times fall within the same
     * millisecond must get distinct update IDs, so both commits to the cache succeed.
     */
    @Test
    void changesWithinSameMillisecondCommitSuccessfully() throws Exception {
        Instant t0 = SampleReports.FIRST;
        Instant t1 = t0.plusNanos(100_000);
        Instant t2 = t0.plusNanos(200_000);
        assertEquals(t1.toEpochMilli(), t2.toEpochMilli());

        InMemoryIncidentCache cache = new InMemoryIncidentCache();
        IncidentSnapshot baseline = roadSnapshot(t0, SampleReports.road("One lane closed"));
        cache.commit(baseline, List.of());

        IncidentSnapshot second = roadSnapshot(t1, SampleReports.road("Two lanes closed"));
        List<IncidentUpdate> firstUpdates = detector.detect(Optional.of(baseline), second);
        cache.commit(second, firstUpdates);

        IncidentSnapshot third = roadSnapshot(t2, SampleReports.road("Three lanes closed"));
        List<IncidentUpdate> secondUpdates = detector.detect(Optional.of(second), third);
        assertDoesNotThrow(() -> cache.commit(third, secondUpdates));

        List<IncidentUpdate> history = cache.find(SampleReports.ROAD_KEY).orElseThrow().getUpdates();
        assertEquals(2, history.size());
        assertNotEquals(history.get(0).getId(), history.get(1).getId());
    }

    /*
    HELPERS
     */

    /**
     * Builds a TTC snapshot holding one alert with the given scopes and periods.
     */
    private static IncidentSnapshot ttcSnapshot(Instant retrievedAt, List<TransitScope> scopes,
                                                List<EffectivePeriod> periods) {
        Incident alert = Incident.builder(SampleReports.TTC_KEY, "Sample transit disruption")
                .transitScopes(scopes)
                .effectivePeriods(periods)
                .build();
        return new IncidentSnapshot(IncidentSource.TTC, retrievedAt, List.of(alert));
    }

    /**
     * A transit scope for one route/stop pair.
     */
    private static TransitScope scope(String route, String stop) {
        return new TransitScope(Optional.of(route), Optional.of(stop));
    }

    /**
     * Builds a road snapshot at the given time containing the given reports.
     */
    private static IncidentSnapshot roadSnapshot(Instant retrievedAt, Incident... incidents) {
        return new IncidentSnapshot(IncidentSource.TORONTO_ROAD, retrievedAt, List.of(incidents));
    }

    /**
     * A second road report with a different record ID from the sample one.
     */
    private static Incident otherRoadReport() {
        return Incident.builder(OTHER_ROAD_KEY, "Another road restriction")
                .description("Sidewalk closed")
                .build();
    }

    /**
     * Asserts that exactly one update was recorded and returns it.
     */
    private static IncidentUpdate onlyUpdate(List<IncidentUpdate> updates) {
        assertEquals(1, updates.size(), "expected exactly one update");
        return updates.get(0);
    }
}
