package org.example.citywatch.use_case.refresh_reports;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.citywatch.entity.FieldChange;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.IncidentUpdate;
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

    /*
    HELPERS
     */

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
